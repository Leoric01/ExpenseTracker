package org.leoric.expensetracker.transaction.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.transaction.TransactionSpecification;
import org.leoric.expensetracker.transaction.dto.TransactionAmountRateMode;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.PageMetaDto;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionConvertedTotalsDto;
import org.leoric.expensetracker.transaction.dto.TransactionPageItemResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionPageResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionTotalsByAssetDto;
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
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	private final TransactionAttachmentRepository attachmentRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final HoldingRepository holdingRepository;
	private final CategoryRepository categoryRepository;
	private final AssetRepository assetRepository;
	private final TransactionMapper transactionMapper;
	private final ImageService imageService;
	private final ExchangeRateService exchangeRateService;

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
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}
	@Override
	@Transactional(readOnly = true)
	public TransactionPageResponseDto transactionFindAllPageable(User currentUser, UUID trackerId, TransactionFilter filter, Pageable pageable) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Asset displayAsset = tracker.getPreferredDisplayAsset();
		TransactionAmountRateMode rateMode = filter.rateMode() != null ? filter.rateMode() : TransactionAmountRateMode.NOW;

		List<Category> categories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		Map<UUID, List<Category>> childrenByParentId = categories.stream()
				.filter(category -> category.getParent() != null)
				.collect(Collectors.groupingBy(category -> category.getParent().getId()));

		Map<UUID, RootCategoryInfo> rootCategoryByCategoryId = buildRootCategoryMap(categories);

		Set<UUID> explicitCategoryIds = resolveDescendantCategoryIds(filter.categoryId(), childrenByParentId);
		Set<UUID> searchCategoryIds = resolveSearchMatchedCategoryIds(filter.search(), categories, childrenByParentId);

		Optional<Sort.Order> amountSortOrder = getAmountSortOrder(pageable);

		Specification<Transaction> specification = TransactionSpecification.filter(
				trackerId,
				filter,
				explicitCategoryIds,
				searchCategoryIds
		);

		if (amountSortOrder.isPresent()) {
			List<Transaction> matchingTransactions = transactionRepository.findAll(specification);
			TransactionTotalsDto totals = calculateTotals(matchingTransactions, filter.holdingId(), displayAsset, rateMode);
			return buildAmountSortedPageResponse(
					filter,
					pageable,
					amountSortOrder.get(),
					matchingTransactions,
					rootCategoryByCategoryId,
					totals,
					displayAsset
			);
		}

		Pageable normalizedPageable = normalizeTransactionPageable(pageable);
		Page<Transaction> transactionPage = transactionRepository.findAll(specification, normalizedPageable);

		TransactionTotalsDto totals = calculateTotals(
				transactionRepository.findAll(specification),
				filter.holdingId(),
				displayAsset,
				rateMode
		);

		List<TransactionResponseDto> content = enrichWithAssetScale(
				transactionPage.getContent().stream()
						.map(transaction -> toTransactionResponse(transaction, rootCategoryByCategoryId))
						.toList()
		);

		Map<String, Asset> pageAssetByCodeUpper = buildAssetByCodeUpper(transactionPage.getContent());
		Instant nowInstant = Instant.now();
		Map<UUID, Long> convertedAmountById = new HashMap<>();
		for (Transaction transaction : transactionPage.getContent()) {
			convertedAmountById.put(
					transaction.getId(),
					resolveConvertedAmount(transaction, displayAsset, pageAssetByCodeUpper, rateMode, nowInstant)
			);
		}

		List<TransactionPageItemResponseDto> responseContent = content.stream()
				.map(dto -> toPageItemResponse(
						dto,
						convertedAmountById.get(dto.id()),
						displayAsset != null ? displayAsset.getCode() : null,
						displayAsset != null ? displayAsset.getScale() : null
				))
				.toList();

		return new TransactionPageResponseDto(
				responseContent,
				new PageMetaDto(
						transactionPage.getSize(),
						transactionPage.getNumber(),
						transactionPage.getTotalElements(),
						transactionPage.getTotalPages()
				),
				totals
		);
	}

	private TransactionPageResponseDto buildAmountSortedPageResponse(
			TransactionFilter filter,
			Pageable pageable,
			Sort.Order amountSortOrder,
			List<Transaction> matchingTransactions,
			Map<UUID, RootCategoryInfo> rootCategoryByCategoryId,
			TransactionTotalsDto totals,
			Asset displayAsset
	) {
		Map<String, Asset> assetByCodeUpper = buildAssetByCodeUpper(matchingTransactions);
		Instant nowInstant = Instant.now();

		List<AmountSortableTransaction> sortableItems = matchingTransactions.stream()
				.map(transaction -> new AmountSortableTransaction(
						transaction,
						toTransactionResponse(transaction, rootCategoryByCategoryId),
						resolveConvertedAmount(transaction, displayAsset, assetByCodeUpper, filter.rateMode(), nowInstant)
				))
				.sorted(amountSortComparator(amountSortOrder))
				.toList();

		List<TransactionResponseDto> orderedDtosWithScale = enrichWithAssetScale(
				sortableItems.stream().map(AmountSortableTransaction::dto).toList()
		);

		Map<UUID, Long> convertedAmountById = new HashMap<>();
		for (AmountSortableTransaction item : sortableItems) {
			convertedAmountById.putIfAbsent(item.transaction().getId(), item.convertedAmount());
		}

		List<TransactionPageItemResponseDto> orderedItems = orderedDtosWithScale.stream()
				.map(dto -> toPageItemResponse(
						dto,
						convertedAmountById.get(dto.id()),
						displayAsset != null ? displayAsset.getCode() : null,
						displayAsset != null ? displayAsset.getScale() : null
				))
				.toList();

		long totalElements = orderedItems.size();
		int fromIndex = (int) Math.min(pageable.getOffset(), totalElements);
		int toIndex = (int) Math.min((long) fromIndex + pageable.getPageSize(), totalElements);
		List<TransactionPageItemResponseDto> pageContent = orderedItems.subList(fromIndex, toIndex);
		int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) totalElements / pageable.getPageSize());

		return new TransactionPageResponseDto(
				pageContent,
				new PageMetaDto(pageable.getPageSize(), pageable.getPageNumber(), totalElements, totalPages),
				totals
		);
	}

	private Comparator<AmountSortableTransaction> amountSortComparator(Sort.Order order) {
		Comparator<Long> convertedComparator = order.isAscending()
				? Comparator.nullsFirst(Long::compareTo)
				: Comparator.nullsLast(Comparator.reverseOrder());

		return Comparator
				.comparing(AmountSortableTransaction::convertedAmount, convertedComparator)
				.thenComparing((left, right) -> right.transaction().getTransactionDate().compareTo(left.transaction().getTransactionDate()))
				.thenComparing((left, right) -> right.transaction().getId().compareTo(left.transaction().getId()));
	}

	private Long resolveConvertedAmount(
			Transaction transaction,
			Asset displayAsset,
			Map<String, Asset> assetByCodeUpper,
			TransactionAmountRateMode rateMode,
			Instant nowInstant
	) {
		if (displayAsset == null || transaction.getCurrencyCode() == null) {
			return null;
		}

		TransactionAmountRateMode effectiveRateMode = rateMode != null ? rateMode : TransactionAmountRateMode.NOW;
		Instant rateInstant = effectiveRateMode == TransactionAmountRateMode.TRANSACTION_DATE
				? transaction.getTransactionDate()
				: nowInstant;

		if (rateInstant == null) {
			rateInstant = nowInstant;
		}

		return convertToDisplayAmount(
				transaction.getAmount(),
				transaction.getCurrencyCode().toUpperCase(),
				displayAsset,
				assetByCodeUpper,
				rateInstant
		);
	}

	private Map<String, Asset> buildAssetByCodeUpper(List<Transaction> transactions) {
		Set<String> codesUpper = transactions.stream()
				.map(Transaction::getCurrencyCode)
				.filter(Objects::nonNull)
				.map(String::toUpperCase)
				.collect(Collectors.toSet());

		if (codesUpper.isEmpty()) {
			return Map.of();
		}

		return assetRepository.findAllByCodeUpperIn(codesUpper).stream()
				.collect(Collectors.toMap(asset -> asset.getCode().toUpperCase(), Function.identity()));
	}

	private Optional<Sort.Order> getAmountSortOrder(Pageable pageable) {
		return pageable.getSort().stream()
				.filter(order -> "amount".equals(order.getProperty()))
				.findFirst();
	}

	private TransactionPageItemResponseDto toPageItemResponse(
			TransactionResponseDto dto,
			Long convertedAmount,
			String convertedInto,
			Integer convertedAssetScale
	) {
		return new TransactionPageItemResponseDto(
				dto.id(),
				dto.transactionType(),
				dto.status(),
				dto.holdingId(),
				dto.holdingName(),
				dto.sourceHoldingId(),
				dto.sourceHoldingName(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.categoryId(),
				dto.categoryName(),
				dto.rootCategoryId(),
				dto.rootCategoryName(),
				dto.amount(),
				dto.assetCode(),
				dto.assetScale(),
				dto.exchangeRate(),
				dto.feeAmount(),
				dto.settledAmount(),
				dto.balanceAdjustmentDirection(),
				dto.transactionDate(),
				dto.description(),
				dto.note(),
				dto.externalRef(),
				dto.attachments(),
				dto.createdDate(),
				dto.lastModifiedDate(),
				convertedAmount,
				convertedInto,
				convertedAssetScale
		);
	}

	@Override
	@Transactional
	public TransactionResponseDto transactionUpdate(User currentUser, UUID trackerId, UUID transactionId, UpdateTransactionRequestDto request) {
		Transaction transaction = getTransactionOrThrow(transactionId);
		assertTransactionBelongsToTracker(transaction, trackerId);
		assertNotCancelled(transaction);

		if (transaction.getTransactionType() == TransactionType.TRANSFER
				|| transaction.getTransactionType() == TransactionType.BALANCE_ADJUSTMENT) {
			if (request.holdingId() != null
					|| request.amount() != null
					|| request.currencyCode() != null
					|| request.exchangeRate() != null
					|| request.feeAmount() != null) {
				throw new OperationNotPermittedException(
						"Financial fields (holding, amount, currency, exchange rate, fee) cannot be changed on a %s transaction"
								.formatted(transaction.getTransactionType()));
			}
		} else {
			patchIncomeOrExpenseFinancialFields(transaction, trackerId, request);
		}

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
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}

	private void patchIncomeOrExpenseFinancialFields(Transaction transaction, UUID trackerId, UpdateTransactionRequestDto request) {
		boolean financialPatchRequested = request.holdingId() != null
				|| request.amount() != null
				|| request.currencyCode() != null
				|| request.exchangeRate() != null
				|| request.feeAmount() != null;
		if (!financialPatchRequested) {
			return;
		}

		if (request.feeAmount() != null && request.feeAmount() < 0) {
			throw new OperationNotPermittedException("Fee amount must be zero or positive");
		}

		Holding oldHolding = transaction.getHolding();
		Holding newHolding = oldHolding;
		if (request.holdingId() != null) {
			newHolding = getHoldingOrThrow(request.holdingId());
			assertHoldingBelongsToTracker(newHolding, trackerId);
			assertHoldingActive(newHolding);
		}

		long oldEffect = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();

		long newAmount = request.amount() != null ? request.amount() : transaction.getAmount();
		long newFeeAmount = request.feeAmount() != null ? request.feeAmount() : transaction.getFeeAmount();
		String newCurrencyCode = request.currencyCode() != null
				? request.currencyCode().toUpperCase()
				: transaction.getCurrencyCode();

		String holdingAssetCode = newHolding.getAsset().getCode();
		boolean crossCurrency = !newCurrencyCode.equals(holdingAssetCode);
		boolean currencyChanged = request.currencyCode() != null
				&& !request.currencyCode().equalsIgnoreCase(transaction.getCurrencyCode());
		boolean holdingChanged = request.holdingId() != null
				&& !request.holdingId().equals(oldHolding.getId());

		BigDecimal newExchangeRate = null;
		Long newSettledAmount = null;
		long newEffect;

		if (crossCurrency) {
			BigDecimal effectiveRate = request.exchangeRate();
			if (effectiveRate == null && !currencyChanged && !holdingChanged) {
				effectiveRate = transaction.getExchangeRate();
			}
			if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
				throw new OperationNotPermittedException(
						"Exchange rate is required for cross-currency transactions (transaction %s, holding %s)"
								.formatted(newCurrencyCode, holdingAssetCode));
			}
			newExchangeRate = effectiveRate;
			newSettledAmount = BigDecimal.valueOf(newAmount)
					.multiply(newExchangeRate)
					.setScale(0, RoundingMode.HALF_UP)
					.longValueExact() + newFeeAmount;
			newEffect = newSettledAmount;
		} else {
			newEffect = newAmount;
		}

		if (transaction.getTransactionType() == TransactionType.INCOME) {
			oldHolding.setCurrentAmount(oldHolding.getCurrentAmount() - oldEffect);
			newHolding.setCurrentAmount(newHolding.getCurrentAmount() + newEffect);
		} else {
			oldHolding.setCurrentAmount(oldHolding.getCurrentAmount() + oldEffect);
			newHolding.setCurrentAmount(newHolding.getCurrentAmount() - newEffect);
		}

		if (oldHolding.getId().equals(newHolding.getId())) {
			holdingRepository.save(oldHolding);
		} else {
			holdingRepository.save(oldHolding);
			holdingRepository.save(newHolding);
		}

		transaction.setHolding(newHolding);
		transaction.setAmount(newAmount);
		transaction.setCurrencyCode(newCurrencyCode);
		transaction.setExchangeRate(newExchangeRate);
		transaction.setFeeAmount(newFeeAmount);
		transaction.setSettledAmount(newSettledAmount);
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
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}
	private TransactionResponseDto toTransactionResponse(Transaction transaction, Map<UUID, RootCategoryInfo> rootCategoryByCategoryId) {
		TransactionResponseDto dto = transactionMapper.toResponse(transaction);

		if (dto.categoryId() == null) {
			return dto;
		}

		RootCategoryInfo rootCategory = rootCategoryByCategoryId.get(dto.categoryId());

		if (rootCategory == null) {
			return dto;
		}

		return new TransactionResponseDto(
				dto.id(),
				dto.transactionType(),
				dto.status(),
				dto.holdingId(),
				dto.holdingName(),
				dto.sourceHoldingId(),
				dto.sourceHoldingName(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.categoryId(),
				dto.categoryName(),
				rootCategory.id(),
				rootCategory.name(),
				dto.amount(),
				dto.assetCode(),
				dto.assetScale(),
				dto.exchangeRate(),
				dto.feeAmount(),
				dto.settledAmount(),
				dto.balanceAdjustmentDirection(),
				dto.transactionDate(),
				dto.description(),
				dto.note(),
				dto.externalRef(),
				dto.attachments(),
				dto.createdDate(),
				dto.lastModifiedDate()
		);
	}

	private TransactionResponseDto enrichWithAssetScale(TransactionResponseDto dto) {
		if (dto.assetCode() == null) {
			return dto;
		}
		Integer scale = assetRepository.findByCodeIgnoreCase(dto.assetCode())
				.map(Asset::getScale)
				.orElse(null);
		return withAssetScale(dto, scale);
	}

	private List<TransactionResponseDto> enrichWithAssetScale(List<TransactionResponseDto> dtos) {
		Set<String> codesUpper = dtos.stream()
				.map(TransactionResponseDto::assetCode)
				.filter(Objects::nonNull)
				.map(String::toUpperCase)
				.collect(Collectors.toSet());

		if (codesUpper.isEmpty()) {
			return dtos;
		}

		Map<String, Integer> scaleByCodeUpper = assetRepository.findAllByCodeUpperIn(codesUpper)
				.stream()
				.collect(Collectors.toMap(asset -> asset.getCode().toUpperCase(), Asset::getScale));

		return dtos.stream()
				.map(dto -> withAssetScale(dto,
						dto.assetCode() == null ? null : scaleByCodeUpper.get(dto.assetCode().toUpperCase())))
				.toList();
	}

	private TransactionResponseDto withAssetScale(TransactionResponseDto dto, Integer assetScale) {
		return new TransactionResponseDto(
				dto.id(),
				dto.transactionType(),
				dto.status(),
				dto.holdingId(),
				dto.holdingName(),
				dto.sourceHoldingId(),
				dto.sourceHoldingName(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.categoryId(),
				dto.categoryName(),
				dto.rootCategoryId(),
				dto.rootCategoryName(),
				dto.amount(),
				dto.assetCode(),
				assetScale,
				dto.exchangeRate(),
				dto.feeAmount(),
				dto.settledAmount(),
				dto.balanceAdjustmentDirection(),
				dto.transactionDate(),
				dto.description(),
				dto.note(),
				dto.externalRef(),
				dto.attachments(),
				dto.createdDate(),
				dto.lastModifiedDate()
		);
	}

	private TransactionTotalsDto calculateTotals(
			List<Transaction> transactions,
			UUID holdingId,
			Asset displayAsset,
			TransactionAmountRateMode rateMode
	) {
		Map<String, MutableAssetTotals> totalsByAsset = new HashMap<>();

		Set<String> codesUpper = transactions.stream()
				.filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
				.map(Transaction::getCurrencyCode)
				.filter(Objects::nonNull)
				.map(String::toUpperCase)
				.collect(Collectors.toSet());

		Map<String, Asset> assetByCodeUpper = codesUpper.isEmpty()
				? Map.of()
				: assetRepository.findAllByCodeUpperIn(codesUpper).stream()
						.collect(Collectors.toMap(asset -> asset.getCode().toUpperCase(), Function.identity()));

		long convertedIncome = 0;
		long convertedExpense = 0;
		boolean convertedComplete = displayAsset != null;
		Instant nowInstant = Instant.now();

		for (Transaction transaction : transactions) {
			if (transaction.getStatus() != TransactionStatus.COMPLETED) {
				continue;
			}

			long incomeDelta = 0;
			long expenseDelta = 0;

			switch (transaction.getTransactionType()) {
				case INCOME -> incomeDelta += transaction.getAmount();
				case EXPENSE -> expenseDelta += transaction.getAmount();
				case BALANCE_ADJUSTMENT -> {
					if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						incomeDelta += transaction.getAmount();
					} else if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.DEDUCTION) {
						expenseDelta += transaction.getAmount();
					}
				}
				case TRANSFER -> {
					if (holdingId == null) {
						continue;
					}

					boolean outgoing = transaction.getSourceHolding() != null && holdingId.equals(transaction.getSourceHolding().getId());
					boolean incoming = transaction.getTargetHolding() != null && holdingId.equals(transaction.getTargetHolding().getId());

					if (outgoing) {
						expenseDelta += transaction.getAmount();
					}
					if (incoming) {
						incomeDelta += transaction.getAmount();
					}
				}
			}

			if (incomeDelta == 0 && expenseDelta == 0) {
				continue;
			}

			String assetCode = transaction.getCurrencyCode() == null ? null : transaction.getCurrencyCode().toUpperCase();
			if (assetCode != null) {
				MutableAssetTotals totals = totalsByAsset.computeIfAbsent(assetCode, ignored -> new MutableAssetTotals());
				totals.income += incomeDelta;
				totals.expense += expenseDelta;
			}

			if (!convertedComplete) {
				continue;
			}

			Instant rateInstant = rateMode == TransactionAmountRateMode.TRANSACTION_DATE
					? transaction.getTransactionDate()
					: nowInstant;
			if (rateInstant == null) {
				rateInstant = nowInstant;
			}

			if (incomeDelta > 0) {
				Long converted = convertToDisplayAmount(incomeDelta, assetCode, displayAsset, assetByCodeUpper, rateInstant);
				if (converted == null) {
					convertedComplete = false;
				} else {
					convertedIncome += converted;
				}
			}

			if (convertedComplete && expenseDelta > 0) {
				Long converted = convertToDisplayAmount(expenseDelta, assetCode, displayAsset, assetByCodeUpper, rateInstant);
				if (converted == null) {
					convertedComplete = false;
				} else {
					convertedExpense += converted;
				}
			}
		}

		List<TransactionTotalsByAssetDto> byAsset = totalsByAsset.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new TransactionTotalsByAssetDto(
						entry.getKey(),
						assetByCodeUpper.get(entry.getKey()) != null ? assetByCodeUpper.get(entry.getKey()).getScale() : null,
						entry.getValue().income,
						entry.getValue().expense,
						entry.getValue().income - entry.getValue().expense
				))
				.toList();

		TransactionConvertedTotalsDto converted = new TransactionConvertedTotalsDto(
				convertedComplete ? convertedIncome : null,
				convertedComplete ? convertedExpense : null,
				convertedComplete ? convertedIncome - convertedExpense : null,
				displayAsset != null ? displayAsset.getCode() : null
		);

		return new TransactionTotalsDto(byAsset, converted);
	}

	private Long convertToDisplayAmount(
			long amount,
			String sourceAssetCodeUpper,
			Asset displayAsset,
			Map<String, Asset> assetByCodeUpper,
			Instant rateInstant
	) {
		if (displayAsset == null || sourceAssetCodeUpper == null) {
			return null;
		}

		if (displayAsset.getCode().equalsIgnoreCase(sourceAssetCodeUpper)) {
			return amount;
		}

		Asset sourceAsset = assetByCodeUpper.get(sourceAssetCodeUpper);
		if (sourceAsset == null) {
			return null;
		}

		return exchangeRateService.convertAmount(amount, sourceAsset, displayAsset, rateInstant);
	}

	private Pageable normalizeTransactionPageable(Pageable pageable) {
		List<Sort.Order> mappedOrders = pageable.getSort().stream()
				.map(order -> switch (order.getProperty()) {
					case "transactionDate" -> new Sort.Order(order.getDirection(), "transactionDate");
					case "walletName" -> new Sort.Order(order.getDirection(), "holding.account.name");
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
		if (request.holdingId() == null) {
			throw new OperationNotPermittedException("Holding is required for %s transactions".formatted(type));
		}
		if (request.categoryId() == null) {
			throw new OperationNotPermittedException("Category is required for %s transactions".formatted(type));
		}
		if (request.amount() == null || request.amount() <= 0) {
			throw new OperationNotPermittedException("Amount must be a positive number");
		}

		Holding holding = getHoldingOrThrow(request.holdingId());
		assertHoldingBelongsToTracker(holding, tracker.getId());
		assertHoldingActive(holding);

		Category category = getCategoryOrThrow(request.categoryId());
		assertCategoryBelongsToTracker(category, tracker.getId());
		assertCategoryMatchesTransactionType(category, type);

		String holdingAssetCode = holding.getAsset().getCode();
		String txnCurrency = request.currencyCode() != null ? request.currencyCode().toUpperCase() : holdingAssetCode;
		boolean crossCurrency = !txnCurrency.equals(holdingAssetCode);

		BigDecimal exchangeRate = null;
		long feeAmount = request.feeAmount() != null ? request.feeAmount() : 0;
		Long settledAmount = null;
		long balanceEffect;

		if (crossCurrency) {
			if (request.exchangeRate() == null || request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
				throw new OperationNotPermittedException(
						"Exchange rate is required for cross-currency transactions (transaction %s, holding %s)"
								.formatted(txnCurrency, holdingAssetCode));
			}
			exchangeRate = request.exchangeRate();
			settledAmount = BigDecimal.valueOf(request.amount())
					.multiply(exchangeRate)
					.setScale(0, RoundingMode.HALF_UP)
					.longValueExact() + feeAmount;
			balanceEffect = settledAmount;
		} else {
			balanceEffect = request.amount();
		}

		if (type == TransactionType.INCOME) {
			holding.setCurrentAmount(holding.getCurrentAmount() + balanceEffect);
		} else {
			holding.setCurrentAmount(holding.getCurrentAmount() - balanceEffect);
		}
		holdingRepository.save(holding);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(type)
				.status(TransactionStatus.COMPLETED)
				.holding(holding)
				.category(category)
				.amount(request.amount())
				.currencyCode(txnCurrency)
				.exchangeRate(exchangeRate)
				.feeAmount(feeAmount)
				.settledAmount(settledAmount)
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created {} transaction '{}' ({} {}{}) in tracker '{}'",
		         currentUser.getEmail(), type, transaction.getId(), request.amount(), txnCurrency,
		         crossCurrency ? " → " + settledAmount + " " + holdingAssetCode : "", tracker.getName());
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}

	// ── TRANSFER ──

	private TransactionResponseDto createTransfer(User currentUser, ExpenseTracker tracker, CreateTransactionRequestDto request) {
		if (request.sourceHoldingId() == null || request.targetHoldingId() == null) {
			throw new OperationNotPermittedException("Source and target holdings are required for TRANSFER transactions");
		}
		if (request.sourceHoldingId().equals(request.targetHoldingId())) {
			throw new OperationNotPermittedException("Source and target holdings must be different");
		}
		if (request.amount() == null || request.amount() <= 0) {
			throw new OperationNotPermittedException("Amount must be a positive number");
		}

		Holding source = getHoldingOrThrow(request.sourceHoldingId());
		assertHoldingBelongsToTracker(source, tracker.getId());
		assertHoldingActive(source);

		Holding target = getHoldingOrThrow(request.targetHoldingId());
		assertHoldingBelongsToTracker(target, tracker.getId());
		assertHoldingActive(target);

		String sourceAssetCode = source.getAsset().getCode();
		String targetAssetCode = target.getAsset().getCode();
		String txnCurrency = request.currencyCode() != null ? request.currencyCode().toUpperCase() : sourceAssetCode;
		boolean crossCurrency = !sourceAssetCode.equals(targetAssetCode);

		BigDecimal exchangeRate = null;
		long feeAmount = request.feeAmount() != null ? request.feeAmount() : 0;
		Long settledAmount = null;
		long sourceDeduction = request.amount();
		long targetAddition = request.amount();

		if (crossCurrency) {
			if (request.exchangeRate() == null || request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
				throw new OperationNotPermittedException(
						"Exchange rate is required for cross-currency transfers (%s → %s)"
								.formatted(sourceAssetCode, targetAssetCode));
			}
			exchangeRate = request.exchangeRate();
			// amount is in source currency, settledAmount is what target receives
			settledAmount = BigDecimal.valueOf(request.amount())
					.multiply(exchangeRate)
					.setScale(0, RoundingMode.HALF_UP)
					.longValueExact();
			sourceDeduction = request.amount() + feeAmount; // fee charged from source
			targetAddition = settledAmount;
		}

		source.setCurrentAmount(source.getCurrentAmount() - sourceDeduction);
		target.setCurrentAmount(target.getCurrentAmount() + targetAddition);
		holdingRepository.save(source);
		holdingRepository.save(target);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(request.amount())
				.currencyCode(txnCurrency)
				.exchangeRate(exchangeRate)
				.feeAmount(feeAmount)
				.settledAmount(settledAmount)
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created TRANSFER '{}' ({} {}{}) {} → {} in tracker '{}'",
		         currentUser.getEmail(), transaction.getId(), request.amount(), txnCurrency,
		         crossCurrency ? " @" + exchangeRate + " → " + settledAmount + " " + targetAssetCode : "",
		         source.getAccount().getName(), target.getAccount().getName(), tracker.getName());
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}

	// ── BALANCE ADJUSTMENT ──

	private TransactionResponseDto createBalanceAdjustment(User currentUser, ExpenseTracker tracker, CreateTransactionRequestDto request) {
		if (request.holdingId() == null) {
			throw new OperationNotPermittedException("Holding is required for BALANCE_ADJUSTMENT transactions");
		}
		if (request.correctedBalance() == null) {
			throw new OperationNotPermittedException("Corrected balance is required for BALANCE_ADJUSTMENT transactions");
		}

		Holding holding = getHoldingOrThrow(request.holdingId());
		assertHoldingBelongsToTracker(holding, tracker.getId());
		assertHoldingActive(holding);

		long diff = request.correctedBalance() - holding.getCurrentAmount();
		if (diff == 0) {
			throw new OperationNotPermittedException("Corrected balance is the same as the current balance");
		}

		BalanceAdjustmentDirection direction = diff > 0
				? BalanceAdjustmentDirection.ADDITION
				: BalanceAdjustmentDirection.DEDUCTION;
		long absAmount = Math.abs(diff);

		holding.setCurrentAmount(request.correctedBalance());
		holdingRepository.save(holding);

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.BALANCE_ADJUSTMENT)
				.status(TransactionStatus.COMPLETED)
				.holding(holding)
				.amount(absAmount)
				.currencyCode(holding.getAsset().getCode())
				.balanceAdjustmentDirection(direction)
				.transactionDate(request.transactionDate())
				.description(request.description())
				.note(request.note())
				.externalRef(request.externalRef())
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created BALANCE_ADJUSTMENT '{}' ({} {} {}) on holding '{}' in tracker '{}'",
		         currentUser.getEmail(), transaction.getId(), direction, absAmount,
		         holding.getAsset().getCode(), holding.getAccount().getName(), tracker.getName());
		return enrichWithAssetScale(transactionMapper.toResponse(transaction));
	}

	// ── Cancel reversal ──

	private void reverseBalanceEffect(Transaction transaction) {
		switch (transaction.getTransactionType()) {
			case INCOME -> {
				Holding holding = transaction.getHolding();
				long effect = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();
				holding.setCurrentAmount(holding.getCurrentAmount() - effect);
				holdingRepository.save(holding);
			}
			case EXPENSE -> {
				Holding holding = transaction.getHolding();
				long effect = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();
				holding.setCurrentAmount(holding.getCurrentAmount() + effect);
				holdingRepository.save(holding);
			}
			case TRANSFER -> {
				Holding source = transaction.getSourceHolding();
				Holding target = transaction.getTargetHolding();
				long sourceReversal = transaction.getAmount() + transaction.getFeeAmount();
				long targetReversal = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();
				source.setCurrentAmount(source.getCurrentAmount() + sourceReversal);
				target.setCurrentAmount(target.getCurrentAmount() - targetReversal);
				holdingRepository.save(source);
				holdingRepository.save(target);
			}
			case BALANCE_ADJUSTMENT -> {
				Holding holding = transaction.getHolding();
				long reversal = transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
						? -transaction.getAmount()
						: transaction.getAmount();
				holding.setCurrentAmount(holding.getCurrentAmount() + reversal);
				holdingRepository.save(holding);
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
	private Map<UUID, RootCategoryInfo> buildRootCategoryMap(List<Category> categories) {
		Map<UUID, Category> categoryById = categories.stream()
				.collect(Collectors.toMap(Category::getId, Function.identity()));

		Map<UUID, RootCategoryInfo> result = new HashMap<>();

		for (Category category : categories) {
			Category root = findRootCategory(category, categoryById);
			result.put(category.getId(), new RootCategoryInfo(root.getId(), root.getName()));
		}

		return result;
	}

	private Category findRootCategory(Category category, Map<UUID, Category> categoryById) {
		Category current = category;

		while (current.getParent() != null) {
			Category parent = categoryById.get(current.getParent().getId());
			if (parent == null) {
				break;
			}
			current = parent;
		}

		return current;
	}
	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Transaction getTransactionOrThrow(UUID transactionId) {
		return transactionRepository.findById(transactionId)
				.orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
	}

	private Holding getHoldingOrThrow(UUID holdingId) {
		return holdingRepository.findById(holdingId)
				.orElseThrow(() -> new EntityNotFoundException("Holding not found"));
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

	private void assertHoldingBelongsToTracker(Holding holding, UUID trackerId) {
		if (!holding.getAccount().getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Holding not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}

	private void assertHoldingActive(Holding holding) {
		if (!holding.isActive()) {
			throw new OperationNotPermittedException("Holding '%s/%s' is deactivated".formatted(
					holding.getAccount().getName(), holding.getAsset().getCode()));
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

	private static final class MutableAssetTotals {
		private long income;
		private long expense;
	}

	private record RootCategoryInfo(
			UUID id,
			String name
	) {
	}

	private record AmountSortableTransaction(
			Transaction transaction,
			TransactionResponseDto dto,
			Long convertedAmount
	) {
	}
}