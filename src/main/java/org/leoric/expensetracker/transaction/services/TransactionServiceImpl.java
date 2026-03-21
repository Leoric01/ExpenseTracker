package org.leoric.expensetracker.transaction.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.mapstruct.TransactionMapper;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionService;
import org.leoric.expensetracker.wallet.models.Wallet;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final WalletRepository walletRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionMapper transactionMapper;

	@Override
	@Transactional
	public TransactionResponseDto transactionCreate(User currentUser, UUID trackerId, CreateTransactionRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		return switch (request.transactionType()) {
			case INCOME -> createIncomeOrExpense(currentUser, tracker, request, TransactionType.INCOME);
			case EXPENSE -> createIncomeOrExpense(currentUser, tracker, request, TransactionType.EXPENSE);
			case TRANSFER -> createTransfer(currentUser, tracker, request);
			case BALANCE_ADJUSTMENT -> createBalanceAdjustment(currentUser, tracker, request);
		};
	}

	@Override
	@Transactional(readOnly = true)
	public TransactionResponseDto transactionFindById(User currentUser, UUID trackerId, UUID transactionId) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);
		return transactionMapper.toResponse(transaction);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TransactionResponseDto> transactionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return transactionRepository.findByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(transactionMapper::toResponse);
		}
		return transactionRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(transactionMapper::toResponse);
	}

	@Override
	@Transactional
	public TransactionResponseDto transactionUpdate(User currentUser, UUID trackerId, UUID transactionId, UpdateTransactionRequestDto request) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);
		assertNotCancelled(transaction);

		if (request.categoryId() != null) {
			if (transaction.getTransactionType() == TransactionType.TRANSFER
					|| transaction.getTransactionType() == TransactionType.BALANCE_ADJUSTMENT) {
				throw new OperationNotPermittedException("Category cannot be changed on a %s transaction".formatted(transaction.getTransactionType()));
			}
			Category category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
			assertCategoryMatchesTransactionType(category, transaction.getTransactionType());
			transaction.setCategory(category);
		}

		if (request.transactionDate() != null) {
			transaction.setTransactionDate(request.transactionDate());
		}
		if (request.description() != null) {
			transaction.setDescription(request.description());
		}
		if (request.note() != null) {
			transaction.setNote(request.note());
		}
		if (request.externalRef() != null) {
			transaction.setExternalRef(request.externalRef());
		}

		transaction = transactionRepository.save(transaction);
		log.info("User {} updated transaction '{}' in tracker '{}'",
				currentUser.getEmail(), transaction.getId(), transaction.getExpenseTracker().getName());
		return transactionMapper.toResponse(transaction);
	}

	@Override
	@Transactional
	public TransactionResponseDto transactionCancel(User currentUser, UUID trackerId, UUID transactionId) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);
		assertNotCancelled(transaction);

		reverseBalanceEffect(transaction);
		transaction.setStatus(TransactionStatus.CANCELLED);
		transaction = transactionRepository.save(transaction);

		log.info("User {} cancelled transaction '{}' in tracker '{}'",
				currentUser.getEmail(), transaction.getId(), transaction.getExpenseTracker().getName());
		return transactionMapper.toResponse(transaction);
	}

	// ── INCOME / EXPENSE ──

	private TransactionResponseDto createIncomeOrExpense(User currentUser, ExpenseTracker tracker,
	                                                     CreateTransactionRequestDto request, TransactionType type) {
		if (request.walletId() == null) {
			throw new OperationNotPermittedException("Wallet is required for %s transactions".formatted(type));
		}
		if (request.categoryId() == null) {
			throw new OperationNotPermittedException("Category is required for %s transactions".formatted(type));
		}
		if (request.amount() == null || request.amount() <= 0) {
			throw new OperationNotPermittedException("Amount must be a positive number");
		}

		Wallet wallet = getWalletOrThrow(request.walletId());
		assertWalletBelongsToTracker(wallet, tracker.getId());
		assertWalletActive(wallet);

		Category category = getCategoryOrThrow(request.categoryId());
		assertCategoryBelongsToTracker(category, tracker.getId());
		assertCategoryMatchesTransactionType(category, type);

		if (type == TransactionType.INCOME) {
			wallet.setCurrentBalance(wallet.getCurrentBalance() + request.amount());
		} else {
			wallet.setCurrentBalance(wallet.getCurrentBalance() - request.amount());
		}
		walletRepository.save(wallet);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(type)
				.status(TransactionStatus.COMPLETED)
				.wallet(wallet)
				.category(category)
				.amount(request.amount())
				.currencyCode(wallet.getCurrencyCode())
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created {} transaction '{}' ({} {}) in tracker '{}'",
				currentUser.getEmail(), type, transaction.getId(), request.amount(), wallet.getCurrencyCode(), tracker.getName());
		return transactionMapper.toResponse(transaction);
	}

	// ── TRANSFER ──

	private TransactionResponseDto createTransfer(User currentUser, ExpenseTracker tracker, CreateTransactionRequestDto request) {
		if (request.sourceWalletId() == null || request.targetWalletId() == null) {
			throw new OperationNotPermittedException("Source and target wallets are required for TRANSFER transactions");
		}
		if (request.sourceWalletId().equals(request.targetWalletId())) {
			throw new OperationNotPermittedException("Source and target wallets must be different");
		}
		if (request.amount() == null || request.amount() <= 0) {
			throw new OperationNotPermittedException("Amount must be a positive number");
		}

		Wallet source = getWalletOrThrow(request.sourceWalletId());
		assertWalletBelongsToTracker(source, tracker.getId());
		assertWalletActive(source);

		Wallet target = getWalletOrThrow(request.targetWalletId());
		assertWalletBelongsToTracker(target, tracker.getId());
		assertWalletActive(target);

		source.setCurrentBalance(source.getCurrentBalance() - request.amount());
		target.setCurrentBalance(target.getCurrentBalance() + request.amount());
		walletRepository.save(source);
		walletRepository.save(target);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceWallet(source)
				.targetWallet(target)
				.amount(request.amount())
				.currencyCode(source.getCurrencyCode())
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created TRANSFER '{}' ({} {}) {} → {} in tracker '{}'",
				currentUser.getEmail(), transaction.getId(), request.amount(), source.getCurrencyCode(),
				source.getName(), target.getName(), tracker.getName());
		return transactionMapper.toResponse(transaction);
	}

	// ── BALANCE ADJUSTMENT ──

	private TransactionResponseDto createBalanceAdjustment(User currentUser, ExpenseTracker tracker, CreateTransactionRequestDto request) {
		if (request.walletId() == null) {
			throw new OperationNotPermittedException("Wallet is required for BALANCE_ADJUSTMENT transactions");
		}
		if (request.correctedBalance() == null) {
			throw new OperationNotPermittedException("Corrected balance is required for BALANCE_ADJUSTMENT transactions");
		}

		Wallet wallet = getWalletOrThrow(request.walletId());
		assertWalletBelongsToTracker(wallet, tracker.getId());
		assertWalletActive(wallet);

		long diff = request.correctedBalance() - wallet.getCurrentBalance();
		if (diff == 0) {
			throw new OperationNotPermittedException("Corrected balance is the same as the current balance");
		}

		BalanceAdjustmentDirection direction = diff > 0
				? BalanceAdjustmentDirection.ADDITION
				: BalanceAdjustmentDirection.DEDUCTION;
		long absAmount = Math.abs(diff);

		wallet.setCurrentBalance(request.correctedBalance());
		walletRepository.save(wallet);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.BALANCE_ADJUSTMENT)
				.status(TransactionStatus.COMPLETED)
				.wallet(wallet)
				.amount(absAmount)
				.currencyCode(wallet.getCurrencyCode())
				.balanceAdjustmentDirection(direction)
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created BALANCE_ADJUSTMENT '{}' ({} {} {}) on wallet '{}' in tracker '{}'",
				currentUser.getEmail(), transaction.getId(), direction, absAmount,
				wallet.getCurrencyCode(), wallet.getName(), tracker.getName());
		return transactionMapper.toResponse(transaction);
	}

	// ── Cancel reversal ──

	private void reverseBalanceEffect(Transaction transaction) {
		switch (transaction.getTransactionType()) {
			case INCOME -> {
				Wallet wallet = transaction.getWallet();
				wallet.setCurrentBalance(wallet.getCurrentBalance() - transaction.getAmount());
				walletRepository.save(wallet);
			}
			case EXPENSE -> {
				Wallet wallet = transaction.getWallet();
				wallet.setCurrentBalance(wallet.getCurrentBalance() + transaction.getAmount());
				walletRepository.save(wallet);
			}
			case TRANSFER -> {
				Wallet source = transaction.getSourceWallet();
				Wallet target = transaction.getTargetWallet();
				source.setCurrentBalance(source.getCurrentBalance() + transaction.getAmount());
				target.setCurrentBalance(target.getCurrentBalance() - transaction.getAmount());
				walletRepository.save(source);
				walletRepository.save(target);
			}
			case BALANCE_ADJUSTMENT -> {
				Wallet wallet = transaction.getWallet();
				long reversal = transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
						? -transaction.getAmount()
						: transaction.getAmount();
				wallet.setCurrentBalance(wallet.getCurrentBalance() + reversal);
				walletRepository.save(wallet);
			}
		}
	}

	// ── Helpers ──

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Transaction getTransactionOrThrow(UUID transactionId) {
		return transactionRepository.findById(transactionId)
				.orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
	}

	private Wallet getWalletOrThrow(UUID walletId) {
		return walletRepository.findById(walletId)
				.orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertTransactionBelongsToTracker(Transaction transaction, UUID trackerId) {
		if (!transaction.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Transaction not found in this expense tracker");
		}
	}

	private void assertWalletBelongsToTracker(Wallet wallet, UUID trackerId) {
		if (!wallet.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Wallet not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}

	private void assertWalletActive(Wallet wallet) {
		if (!wallet.isActive()) {
			throw new OperationNotPermittedException("Wallet '%s' is deactivated".formatted(wallet.getName()));
		}
	}

	private void assertNotCancelled(Transaction transaction) {
		if (transaction.getStatus() == TransactionStatus.CANCELLED) {
			throw new OperationNotPermittedException("Transaction is already cancelled");
		}
	}

	private void assertCategoryMatchesTransactionType(Category category, TransactionType type) {
		CategoryKind expectedKind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
		if (category.getCategoryKind() != expectedKind) {
			throw new OperationNotPermittedException(
					"Category '%s' is of kind %s but transaction type is %s".formatted(
							category.getName(), category.getCategoryKind(), type));
		}
	}
}