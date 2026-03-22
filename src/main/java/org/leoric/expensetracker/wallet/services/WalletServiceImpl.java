package org.leoric.expensetracker.wallet.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateWalletNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.wallet.dto.CategoryBreakdownDto;
import org.leoric.expensetracker.wallet.dto.CreateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.UpdateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.WalletDashboardResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletSummaryResponseDto;
import org.leoric.expensetracker.wallet.mapstruct.WalletMapper;
import org.leoric.expensetracker.wallet.models.Wallet;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
import org.leoric.expensetracker.wallet.services.interfaces.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

	private final WalletRepository walletRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final TransactionRepository transactionRepository;
	private final WidgetItemService widgetItemService;
	private final WalletMapper walletMapper;
	private final ImageService imageService;

	@Override
	@Transactional
	public WalletResponseDto walletCreate(User currentUser, UUID trackerId, CreateWalletRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (walletRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new DuplicateWalletNameException(
					"Wallet with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		long initialBalance = request.initialBalance() != null ? request.initialBalance() : 0L;

		Wallet wallet = Wallet.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.walletType(request.walletType())
				.currencyCode(request.currencyCode().toUpperCase())
				.initialBalance(initialBalance)
				.currentBalance(initialBalance)
				.build();

		wallet = walletRepository.save(wallet);
		log.info("User {} created wallet '{}' in tracker '{}'", currentUser.getEmail(), wallet.getName(), tracker.getName());

		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional(readOnly = true)
	public WalletResponseDto walletFindById(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<WalletResponseDto> walletFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return walletRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(walletMapper::toResponse);
		}
		return walletRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(walletMapper::toResponse);
	}

	@Override
	@Transactional
	public WalletResponseDto walletUpdate(User currentUser, UUID trackerId, UUID walletId, UpdateWalletRequestDto request) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		walletMapper.updateFromDto(request, wallet);
		wallet = walletRepository.save(wallet);

		log.info("User {} updated wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional
	public void walletDeactivate(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		if (!wallet.isActive()) {
			throw new OperationNotPermittedException("Wallet is already deactivated");
		}

		wallet.setActive(false);
		walletRepository.save(wallet);
		log.info("User {} deactivated wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public WalletResponseDto walletUploadIcon(User currentUser, UUID trackerId, UUID walletId, MultipartFile icon, String iconColor) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		String iconUrl = imageService.uploadImage(icon, "expense-tracker/wallets");
		wallet.setIconUrl(iconUrl);
		wallet.setIconColor(iconColor);
		wallet = walletRepository.save(wallet);

		log.info("User {} uploaded icon for wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional
	public WalletResponseDto walletDeleteIcon(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		wallet.setIconUrl(null);
		wallet.setIconColor(null);
		wallet = walletRepository.save(wallet);

		log.info("User {} deleted icon for wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional(readOnly = true)
	public WalletSummaryResponseDto walletSummary(User currentUser, UUID trackerId, UUID walletId, Instant from, Instant to) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);
		return buildSummary(wallet, from, to);
	}

	@Override
	@Transactional(readOnly = true)
	public WalletDashboardResponseDto walletDashboard(User currentUser, UUID trackerId, Instant from, Instant to) {
		List<Wallet> wallets = walletRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		List<WalletSummaryResponseDto> summaries = wallets.stream()
				.map(w -> buildSummary(w, from, to))
				.toList();

		List<WidgetItemResponseDto> widgetOrder = widgetItemService.widgetItemFindAll(currentUser, WidgetType.WALLET);

		return new WalletDashboardResponseDto(from, to, widgetOrder, summaries);
	}

	private WalletSummaryResponseDto buildSummary(Wallet wallet, Instant from, Instant to) {
		UUID walletId = wallet.getId();
		Instant now = Instant.now();

		// All completed transactions from period start to NOW — needed to compute startBalance
		List<Transaction> allFromStartToNow = transactionRepository.findCompletedByWalletAndDateRange(walletId, from, now);

		// startBalance = currentBalance - netEffect(from → now)
		long netFromStartToNow = computeNetEffect(allFromStartToNow, walletId);
		long startBalance = wallet.getCurrentBalance() - netFromStartToNow;

		// Period transactions [from, to)
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
				if (t.getSourceWallet() != null && t.getSourceWallet().getId().equals(walletId)) {
					totalTransferOut += t.getAmount();
					totalExpense += t.getAmount();
				}
				if (t.getTargetWallet() != null && t.getTargetWallet().getId().equals(walletId)) {
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
		long netPeriod = computeNetEffect(periodTxns, walletId);
		long endBalance = startBalance + netPeriod;

		List<CategoryBreakdownDto> incomeByCategory = incomeBuckets.values().stream()
				.sorted(Comparator.comparingLong(CategoryBucket::total).reversed())
				.map(b -> new CategoryBreakdownDto(b.categoryId, b.categoryName, b.total))
				.toList();

		List<CategoryBreakdownDto> expenseByCategory = expenseBuckets.values().stream()
				.sorted(Comparator.comparingLong(CategoryBucket::total).reversed())
				.map(b -> new CategoryBreakdownDto(b.categoryId, b.categoryName, b.total))
				.toList();

		return new WalletSummaryResponseDto(
				wallet.getId(), wallet.getName(), wallet.getCurrencyCode(),
				from, to,
				startBalance, endBalance,
				totalIncome, totalExpense,
				totalTransferIn, totalTransferOut,
				difference,
				incomeByCategory, expenseByCategory
		);
	}

	// ── Helpers ──

	private long computeNetEffect(List<Transaction> transactions, UUID walletId) {
		long net = 0;
		for (Transaction t : transactions) {
			switch (t.getTransactionType()) {
				case INCOME -> net += t.getAmount();
				case EXPENSE -> net -= t.getAmount();
				case TRANSFER -> {
					if (t.getSourceWallet() != null && t.getSourceWallet().getId().equals(walletId)) {
						net -= t.getAmount();
					}
					if (t.getTargetWallet() != null && t.getTargetWallet().getId().equals(walletId)) {
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

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Wallet getWalletOrThrow(UUID walletId) {
		return walletRepository.findById(walletId)
				.orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
	}

	private void assertWalletBelongsToTracker(Wallet wallet, UUID trackerId) {
		if (!wallet.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Wallet not found in this expense tracker");
		}
	}
}