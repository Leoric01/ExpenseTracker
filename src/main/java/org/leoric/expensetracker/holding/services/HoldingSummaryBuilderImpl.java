package org.leoric.expensetracker.holding.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.holding.dto.CategoryBreakdownDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingSummaryBuilderImpl implements HoldingSummaryBuilder {

	private final TransactionRepository transactionRepository;

	@Override
	public HoldingSummaryResponseDto buildSummary(Holding holding, Instant from, Instant to) {
		UUID holdingId = holding.getId();
		Instant now = Instant.now();

		List<Transaction> allFromStartToNow = transactionRepository.findCompletedByHoldingAndDateRange(holdingId, from, now);

		long netFromStartToNow = computeNetEffect(allFromStartToNow, holdingId);
		long startBalance = holding.getCurrentAmount() - netFromStartToNow;

		List<Transaction> periodTxns = allFromStartToNow.stream()
				.filter(t -> t.getTransactionDate().isBefore(to))
				.toList();

		long totalIncome = 0;
		long totalExpense = 0;
		long totalTransferIn = 0;
		long totalTransferOut = 0;

		Map<UUID, CategoryBucket> incomeBuckets = new HashMap<>();
		Map<UUID, CategoryBucket> expenseBuckets = new HashMap<>();

		for (Transaction t : periodTxns) {
			switch (t.getTransactionType()) {
				case INCOME -> {
					totalIncome += t.getAmount();
					if (t.getCategory() != null) {
						incomeBuckets.computeIfAbsent(t.getCategory().getId(),
								_ -> new CategoryBucket(t.getCategory().getId(), t.getCategory().getName())).total += t.getAmount();
					}
				}
				case EXPENSE -> {
					totalExpense += t.getAmount();
					if (t.getCategory() != null) {
						expenseBuckets.computeIfAbsent(t.getCategory().getId(),
								_ -> new CategoryBucket(t.getCategory().getId(), t.getCategory().getName())).total += t.getAmount();
					}
				}
				case TRANSFER -> {
					if (t.getSourceHolding() != null && t.getSourceHolding().getId().equals(holdingId)) {
						totalTransferOut += t.getAmount();
						totalExpense += t.getAmount();
					}
					if (t.getTargetHolding() != null && t.getTargetHolding().getId().equals(holdingId)) {
						totalTransferIn += t.getAmount();
						totalIncome += t.getAmount();
					}
				}
				case BALANCE_ADJUSTMENT -> {
					if (t.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						totalIncome += t.getAmount();
					} else {
						totalExpense += t.getAmount();
					}
				}
			}
		}

		long difference = totalIncome - totalExpense;
		long netPeriod = computeNetEffect(periodTxns, holdingId);
		long endBalance = startBalance + netPeriod;

		List<CategoryBreakdownDto> incomeByCategory = incomeBuckets.values().stream()
				.sorted(Comparator.comparingLong(CategoryBucket::total).reversed())
				.map(b -> new CategoryBreakdownDto(b.categoryId, b.categoryName, b.total))
				.toList();

		List<CategoryBreakdownDto> expenseByCategory = expenseBuckets.values().stream()
				.sorted(Comparator.comparingLong(CategoryBucket::total).reversed())
				.map(b -> new CategoryBreakdownDto(b.categoryId, b.categoryName, b.total))
				.toList();

		return new HoldingSummaryResponseDto(
				holding.getId(),
				holding.getAccount().getName(),
				holding.getAccount().getInstitution().getName(),
				holding.getAsset().getCode(),
				from, to,
				startBalance, endBalance,
				totalIncome, totalExpense,
				totalTransferIn, totalTransferOut,
				difference,
				incomeByCategory, expenseByCategory
		);
	}

	private long computeNetEffect(List<Transaction> transactions, UUID holdingId) {
		long net = 0;
		for (Transaction t : transactions) {
			switch (t.getTransactionType()) {
				case INCOME -> net += t.getAmount();
				case EXPENSE -> net -= t.getAmount();
				case TRANSFER -> {
					if (t.getSourceHolding() != null && t.getSourceHolding().getId().equals(holdingId)) {
						net -= t.getAmount();
					}
					if (t.getTargetHolding() != null && t.getTargetHolding().getId().equals(holdingId)) {
						net += t.getAmount();
					}
				}
				case BALANCE_ADJUSTMENT -> {
					if (t.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						net += t.getAmount();
					} else {
						net -= t.getAmount();
					}
				}
			}
		}
		return net;
	}

	private static class CategoryBucket {
		final UUID categoryId;
		final String categoryName;
		long total;

		CategoryBucket(UUID categoryId, String categoryName) {
			this.categoryId = categoryId;
			this.categoryName = categoryName;
		}

		long total() {
			return total;
		}
	}
}