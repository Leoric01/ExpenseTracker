package org.leoric.expensetracker.holding.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.dto.CategoryBreakdownDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HoldingSummaryBuilderImpl implements HoldingSummaryBuilder {

	private final TransactionRepository transactionRepository;

	@Override
	public HoldingSummaryResponseDto buildSummary(Holding holding, Instant from, Instant to) {
		UUID holdingId = holding.getId();
		Instant now = Instant.now();

		List<Transaction> allFromStart = transactionRepository.findCompletedByHoldingFrom(holdingId, from);

		long netFromStart = computeNetEffect(allFromStart, holdingId);
		long startBalance = Math.subtractExact(holding.getCurrentAmount(), netFromStart);
		Instant createdDate = holding.getCreatedDate();
		boolean createdAfterStart = createdDate != null && !createdDate.isBefore(from) && createdDate.isBefore(now);
		if (createdAfterStart) {
			startBalance = Math.subtractExact(startBalance, holding.getInitialAmount());
		}

		List<Transaction> periodTxns = allFromStart.stream()
				.filter(t -> t.getTransactionDate().isBefore(to) && t.getTransactionDate().isBefore(now))
				.toList();

		long totalIncome = 0;
		long totalExpense = 0;
		long totalTransferIn = 0;
		long totalTransferOut = 0;

		Map<UUID, CategoryBucket> incomeBuckets = new HashMap<>();
		Map<UUID, CategoryBucket> expenseBuckets = new HashMap<>();

		long initialEffect = createdAfterStart && createdDate.isBefore(to) ? holding.getInitialAmount() : 0;
		if (initialEffect > 0) {
			totalIncome = Math.addExact(totalIncome, initialEffect);
		} else if (initialEffect < 0) {
			totalExpense = Math.subtractExact(totalExpense, initialEffect);
		}

		for (Transaction t : periodTxns) {
			switch (t.getTransactionType()) {
				case INCOME -> {
					long amount = resolveIncomeExpenseBalanceEffect(t);
					totalIncome = Math.addExact(totalIncome, amount);
					if (t.getCategory() != null) {
						CategoryBucket bucket = incomeBuckets.computeIfAbsent(t.getCategory().getId(),
								_ -> new CategoryBucket(t.getCategory().getId(), t.getCategory().getName()));
						bucket.total = Math.addExact(bucket.total, amount);
					}
				}
				case EXPENSE, TEST -> {
					long amount = resolveIncomeExpenseBalanceEffect(t);
					totalExpense = Math.addExact(totalExpense, amount);
					if (t.getCategory() != null) {
						CategoryBucket bucket = expenseBuckets.computeIfAbsent(t.getCategory().getId(),
								_ -> new CategoryBucket(t.getCategory().getId(), t.getCategory().getName()));
						bucket.total = Math.addExact(bucket.total, amount);
					}
				}
				case TRANSFER -> {
					if (t.getSourceHolding() != null && t.getSourceHolding().getId().equals(holdingId)) {
						totalTransferOut = Math.addExact(totalTransferOut, t.getAmount());
						totalExpense = Math.addExact(totalExpense, t.getAmount());
					}
					if (t.getTargetHolding() != null && t.getTargetHolding().getId().equals(holdingId)) {
						long transferInAmount = resolveTransferSettledAmount(t);
						totalTransferIn = Math.addExact(totalTransferIn, transferInAmount);
						totalIncome = Math.addExact(totalIncome, transferInAmount);
					}
				}
				case BALANCE_ADJUSTMENT -> {
					if (t.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						totalIncome = Math.addExact(totalIncome, t.getAmount());
					} else {
						totalExpense = Math.addExact(totalExpense, t.getAmount());
					}
				}
			}
		}

		long difference = Math.subtractExact(totalIncome, totalExpense);
		long netPeriod = Math.addExact(computeNetEffect(periodTxns, holdingId), initialEffect);
		long endBalance = Math.addExact(startBalance, netPeriod);

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
				holding.getAsset().getScale(),
				from, to,
				startBalance, endBalance,
				totalIncome, totalExpense,
				totalTransferIn, totalTransferOut,
				difference,
				incomeByCategory, expenseByCategory,
				null, null, null // converted fields — populated later in dashboard
		);
	}

	private long computeNetEffect(List<Transaction> transactions, UUID holdingId) {
		long net = 0;
		for (Transaction t : transactions) {
			switch (t.getTransactionType()) {
				case INCOME -> net = Math.addExact(net, resolveIncomeExpenseBalanceEffect(t));
				case EXPENSE, TEST -> net = Math.subtractExact(net, resolveIncomeExpenseBalanceEffect(t));
				case TRANSFER -> {
					if (t.getSourceHolding() != null && t.getSourceHolding().getId().equals(holdingId)) {
						net = Math.subtractExact(net, t.getAmount());
					}
					if (t.getTargetHolding() != null && t.getTargetHolding().getId().equals(holdingId)) {
						net = Math.addExact(net, resolveTransferSettledAmount(t));
					}
				}
				case BALANCE_ADJUSTMENT -> {
					if (t.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						net = Math.addExact(net, t.getAmount());
					} else {
						net = Math.subtractExact(net, t.getAmount());
					}
				}
			}
		}
		return net;
	}

	private long resolveIncomeExpenseBalanceEffect(Transaction transaction) {
		if (transaction.getSettledAmount() != null) {
			return transaction.getSettledAmount();
		}
		return transaction.getTransactionType() == TransactionType.INCOME
				? transaction.getAmount()
				: Math.addExact(transaction.getAmount(), transaction.getFeeAmount());
	}

	private boolean isInternalSameAssetTransfer(Transaction transaction) {
		if (transaction.getSourceHolding() == null || transaction.getTargetHolding() == null) {
			return false;
		}
		if (transaction.getSourceHolding().getAsset() == null || transaction.getTargetHolding().getAsset() == null) {
			return false;
		}

		String sourceCode = transaction.getSourceHolding().getAsset().getCode();
		String targetCode = transaction.getTargetHolding().getAsset().getCode();
		if (!sourceCode.equalsIgnoreCase(targetCode)) {
			return false;
		}

		return transaction.getExchangeRate() == null || BigDecimal.ONE.compareTo(transaction.getExchangeRate()) == 0;
	}

	private long resolveTransferSettledAmount(Transaction transaction) {
		if (transaction.getSettledAmount() != null) {
			return transaction.getSettledAmount();
		}

		if (!isInternalSameAssetTransfer(transaction)) {
			throw new OperationNotPermittedException("Převod '%s' nemá zadanou přijatou částku; opravte jej v historii transakcí"
					.formatted(transaction.getId()));
		}
		if (transaction.getFeeAmount() < 0 || transaction.getFeeAmount() > transaction.getAmount()) {
			throw new OperationNotPermittedException("Převod '%s' má neplatný poplatek".formatted(transaction.getId()));
		}
		return Math.subtractExact(transaction.getAmount(), transaction.getFeeAmount());
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