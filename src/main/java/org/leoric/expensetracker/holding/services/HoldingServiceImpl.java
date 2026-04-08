package org.leoric.expensetracker.holding.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.account.repositories.AccountRepository;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.dto.CategoryBreakdownDto;
import org.leoric.expensetracker.holding.dto.CreateHoldingRequestDto;
import org.leoric.expensetracker.holding.dto.HoldingDashboardResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.mapstruct.HoldingMapper;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.interfaces.HoldingService;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingServiceImpl implements HoldingService {

	private final HoldingRepository holdingRepository;
	private final AccountRepository accountRepository;
	private final AssetRepository assetRepository;
	private final TransactionRepository transactionRepository;
	private final WidgetItemService widgetItemService;
	private final HoldingMapper holdingMapper;

	@Override
	@Transactional
	public HoldingResponseDto holdingCreate(User currentUser, UUID trackerId, CreateHoldingRequestDto request) {
		Account account = getAccountOrThrow(request.accountId());
		assertAccountBelongsToTracker(account, trackerId);

		Asset asset = assetRepository.findById(request.assetId())
				.orElseThrow(() -> new EntityNotFoundException("Asset not found"));

		if (holdingRepository.existsByAccountIdAndAssetId(account.getId(), asset.getId())) {
			throw new OperationNotPermittedException(
					"Holding for asset '%s' already exists in account '%s'".formatted(asset.getCode(), account.getName()));
		}

		long initialAmount = request.initialAmount() != null ? request.initialAmount() : 0L;

		Holding holding = Holding.builder()
				.account(account)
				.asset(asset)
				.initialAmount(initialAmount)
				.currentAmount(initialAmount)
				.build();

		holding = holdingRepository.save(holding);
		log.info("User {} created holding '{}/{}' in account '{}' (tracker '{}')",
				currentUser.getEmail(), asset.getCode(), account.getName(),
				account.getInstitution().getName(), account.getInstitution().getExpenseTracker().getName());
		return holdingMapper.toResponse(holding);
	}

	@Override
	@Transactional(readOnly = true)
	public HoldingResponseDto holdingFindById(User currentUser, UUID trackerId, UUID holdingId) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);
		return holdingMapper.toResponse(holding);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<HoldingResponseDto> holdingFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return holdingRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(holdingMapper::toResponse);
		}
		return holdingRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(holdingMapper::toResponse);
	}

	@Override
	@Transactional
	public void holdingDeactivate(User currentUser, UUID trackerId, UUID holdingId) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);

		if (!holding.isActive()) {
			throw new OperationNotPermittedException("Holding is already deactivated");
		}

		holding.setActive(false);
		holdingRepository.save(holding);
		log.info("User {} deactivated holding '{}/{}' in tracker '{}'",
				currentUser.getEmail(), holding.getAsset().getCode(), holding.getAccount().getName(),
				holding.getAccount().getInstitution().getExpenseTracker().getName());
	}

	@Override
	@Transactional(readOnly = true)
	public HoldingSummaryResponseDto holdingSummary(User currentUser, UUID trackerId, UUID holdingId, Instant from, Instant to) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);
		return buildSummary(holding, from, to);
	}

	@Override
	@Transactional(readOnly = true)
	public HoldingDashboardResponseDto holdingDashboard(User currentUser, UUID trackerId, Instant from, Instant to) {
		List<Holding> holdings = holdingRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		List<HoldingSummaryResponseDto> summaries = holdings.stream()
				.map(h -> buildSummary(h, from, to))
				.toList();

		List<WidgetItemResponseDto> widgetOrder = widgetItemService.widgetItemFindAll(currentUser, WidgetType.HOLDING);

		return new HoldingDashboardResponseDto(from, to, widgetOrder, summaries);
	}

	private HoldingSummaryResponseDto buildSummary(Holding holding, Instant from, Instant to) {
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

	private Account getAccountOrThrow(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new EntityNotFoundException("Account not found"));
	}

	private Holding getHoldingOrThrow(UUID holdingId) {
		return holdingRepository.findById(holdingId)
				.orElseThrow(() -> new EntityNotFoundException("Holding not found"));
	}

	private void assertAccountBelongsToTracker(Account account, UUID trackerId) {
		if (!account.getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Account not found in this expense tracker");
		}
	}

	private void assertHoldingBelongsToTracker(Holding holding, UUID trackerId) {
		if (!holding.getAccount().getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Holding not found in this expense tracker");
		}
	}
}