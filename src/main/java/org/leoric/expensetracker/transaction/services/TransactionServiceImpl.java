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
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.transaction.TransactionSpecification;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.PageMetaDto;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionPageResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionTotalsDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.mapstruct.TransactionMapper;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.TransactionAttachment;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionAttachmentRepository;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionService;
import org.leoric.expensetracker.wallet.models.Wallet;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	private final TransactionAttachmentRepository attachmentRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final WalletRepository walletRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionMapper transactionMapper;
	private final ImageService imageService;

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"application/pdf",
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/heic",
			"image/heif"
	);

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
	public TransactionPageResponseDto transactionFindAllPageable(User currentUser, UUID trackerId, TransactionFilter filter, Pageable pageable) {
		List<Category> categories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		Map<UUID, List<Category>> childrenByParentId = categories.stream()
				.filter(category -> category.getParent() != null)
				.collect(Collectors.groupingBy(category -> category.getParent().getId()));

		Set<UUID> explicitCategoryIds = resolveDescendantCategoryIds(filter.categoryId(), childrenByParentId);
		Set<UUID> searchCategoryIds = resolveSearchMatchedCategoryIds(filter.search(), categories, childrenByParentId);

		Pageable normalizedPageable = normalizeTransactionPageable(pageable);

		Specification<Transaction> specification = TransactionSpecification.filter(
				trackerId,
				filter,
				explicitCategoryIds,
				searchCategoryIds
		);

		Page<Transaction> transactionPage = transactionRepository.findAll(specification, normalizedPageable);

		TransactionTotalsDto totals = calculateTotals(
				transactionRepository.findAll(specification),
				filter.walletId()
		);

		return new TransactionPageResponseDto(
				transactionPage.getContent().stream()
						.map(transactionMapper::toResponse)
						.toList(),
				new PageMetaDto(
						transactionPage.getSize(),
						transactionPage.getNumber(),
						transactionPage.getTotalElements(),
						transactionPage.getTotalPages()
				),
				totals
		);
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

	private TransactionTotalsDto calculateTotals(List<Transaction> transactions, UUID walletId) {
		long incomeAmount = 0;
		long expenseAmount = 0;

		for (Transaction transaction : transactions) {
			if (transaction.getStatus() != TransactionStatus.COMPLETED) {
				continue;
			}

			switch (transaction.getTransactionType()) {
				case INCOME -> incomeAmount += transaction.getAmount();
				case EXPENSE -> expenseAmount += transaction.getAmount();
				case BALANCE_ADJUSTMENT -> {
					if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						incomeAmount += transaction.getAmount();
					} else if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.DEDUCTION) {
						expenseAmount += transaction.getAmount();
					}
				}
				case TRANSFER -> {
					if (walletId == null) {
						continue;
					}

					boolean outgoing = transaction.getSourceWallet() != null && walletId.equals(transaction.getSourceWallet().getId());
					boolean incoming = transaction.getTargetWallet() != null && walletId.equals(transaction.getTargetWallet().getId());

					if (outgoing) {
						expenseAmount += transaction.getAmount();
					}
					if (incoming) {
						incomeAmount += transaction.getAmount();
					}
				}
			}
		}

		return new TransactionTotalsDto(
				incomeAmount,
				expenseAmount,
				incomeAmount - expenseAmount
		);
	}

	private Pageable normalizeTransactionPageable(Pageable pageable) {
		List<Sort.Order> mappedOrders = pageable.getSort().stream()
				.map(order -> switch (order.getProperty()) {
					case "transactionDate" -> new Sort.Order(order.getDirection(), "transactionDate");
					case "walletName" -> new Sort.Order(order.getDirection(), "wallet.name");
					case "transactionType" -> new Sort.Order(order.getDirection(), "transactionType");
					default -> null;
				})
				.filter(Objects::nonNull)
				.toList();

		Sort sort = mappedOrders.isEmpty()
				? Sort.by(Sort.Order.desc("transactionDate"))
				: Sort.by(mappedOrders);

		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

	private Set<UUID> resolveDescendantCategoryIds(UUID categoryId, Map<UUID, List<Category>> childrenByParentId) {
		if (categoryId == null) {
			return Set.of();
		}

		Set<UUID> result = new HashSet<>();
		collectDescendants(categoryId, childrenByParentId, result);
		return result;
	}

	private Set<UUID> resolveSearchMatchedCategoryIds(String search, List<Category> categories, Map<UUID, List<Category>> childrenByParentId) {
		if (search == null || search.isBlank()) {
			return Set.of();
		}

		String normalizedSearch = search.toLowerCase();
		Set<UUID> result = new HashSet<>();

		for (Category category : categories) {
			if (category.getName() != null && category.getName().toLowerCase().contains(normalizedSearch)) {
				collectDescendants(category.getId(), childrenByParentId, result);
			}
		}

		return result;
	}

	private void collectDescendants(UUID categoryId, Map<UUID, List<Category>> childrenByParentId, Set<UUID> result) {
		if (!result.add(categoryId)) {
			return;
		}

		for (Category child : childrenByParentId.getOrDefault(categoryId, List.of())) {
			collectDescendants(child.getId(), childrenByParentId, result);
		}
	}

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

	// ── Attachments ──

	@Override
	@Transactional
	public TransactionAttachmentResponseDto transactionUploadAttachment(User currentUser, UUID trackerId, UUID transactionId, MultipartFile file) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new OperationNotPermittedException(
					"Unsupported file type '%s'. Allowed: PDF, JPEG, PNG, WEBP, HEIC".formatted(contentType));
		}

		String fileUrl = imageService.uploadFile(file, "expense-tracker/attachments");

		TransactionAttachment attachment = TransactionAttachment.builder()
				.transaction(transaction)
				.fileName(file.getOriginalFilename())
				.fileUrl(fileUrl)
				.contentType(contentType)
				.fileSize(file.getSize())
				.build();

		attachment = attachmentRepository.save(attachment);
		log.info("User {} uploaded attachment '{}' to transaction '{}' in tracker '{}'",
		         currentUser.getEmail(), attachment.getFileName(), transactionId, trackerId);

		return transactionMapper.toAttachmentResponse(attachment);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TransactionAttachmentResponseDto> transactionFindAttachments(User currentUser, UUID trackerId, UUID transactionId) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);

		return attachmentRepository.findByTransactionId(transactionId).stream()
				.map(transactionMapper::toAttachmentResponse)
				.toList();
	}

	@Override
	@Transactional
	public void transactionDeleteAttachment(User currentUser, UUID trackerId, UUID transactionId, UUID attachmentId) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);

		TransactionAttachment attachment = attachmentRepository.findById(attachmentId)
				.orElseThrow(() -> new EntityNotFoundException("Attachment not found"));

		if (!attachment.getTransaction().getId().equals(transactionId)) {
			throw new EntityNotFoundException("Attachment not found on this transaction");
		}

		attachmentRepository.delete(attachment);
		log.info("User {} deleted attachment '{}' from transaction '{}' in tracker '{}'",
		         currentUser.getEmail(), attachment.getFileName(), transactionId, trackerId);
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