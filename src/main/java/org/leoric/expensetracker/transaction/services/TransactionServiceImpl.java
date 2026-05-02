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
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.transaction.TransactionSpecification;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.PageMetaDto;
import org.leoric.expensetracker.transaction.dto.TransactionAmountRateMode;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionConvertedTotalsDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
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
		return enrichWithAssetScale(normalizeDisplayedExchangeRate(transactionMapper.toResponse(transaction)));
	}

	@Override
	@Transactional(readOnly = true)
	public TransactionPageResponseDto transactionFindAllPageable(User currentUser, UUID trackerId, TransactionFilter filter, Pageable pageable) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Asset displayAsset = tracker.getPreferredDisplayAsset();
		TransactionAmountRateMode rateMode = filter.rateMode() != null ? filter.rateMode() : TransactionAmountRateMode.NOW;
		log.debug("transactionFindAllPageable called: trackerId={}, search='{}', categoryId={}, holdingId={}, type={}, status={}, dateFrom={}, dateTo={}, rateMode={}, pageable={}",
		          trackerId, filter.search(), filter.categoryId(), filter.holdingId(), filter.transactionType(), filter.status(),
		          filter.dateFrom(), filter.dateTo(), rateMode, pageable);

		List<Category> categories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		Map<UUID, List<Category>> childrenByParentId = categories.stream()
				.filter(category -> category.getParent() != null)
				.collect(Collectors.groupingBy(category -> category.getParent().getId()));

		Map<UUID, RootCategoryInfo> rootCategoryByCategoryId = buildRootCategoryMap(categories);

		Set<UUID> explicitCategoryIds = resolveDescendantCategoryIds(filter.categoryId(), childrenByParentId);
		Set<UUID> searchCategoryIds = resolveSearchMatchedCategoryIds(filter.search(), categories, childrenByParentId);
		boolean b = filter.search() != null && !filter.search().isBlank();
		if (b) {
			log.debug("transaction search context: trackerId={}, explicitCategoryIdsCount={}, searchCategoryIdsCount={}",
			          trackerId, explicitCategoryIds.size(), searchCategoryIds.size());
		}

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
		if (b) {
			log.debug("transaction search result page: trackerId={}, search='{}', returnedElements={}, totalElements={}, pageNumber={}, pageSize={}",
			          trackerId, filter.search(), transactionPage.getNumberOfElements(), transactionPage.getTotalElements(),
			          transactionPage.getNumber(), transactionPage.getSize());
			if (transactionPage.getTotalElements() == 0) {
				long trackerTransactionCount = transactionRepository.count((root, _, cb) ->
						                                                           cb.equal(root.get("expenseTracker").get("id"), trackerId));
				log.warn("transaction search returned 0 rows: trackerId={}, search='{}', trackerTransactionCount={}",
				         trackerId, filter.search(), trackerTransactionCount);
			}
		}

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
		Map<UUID, ConvertedAmounts> convertedById = new HashMap<>();
		for (Transaction transaction : transactionPage.getContent()) {
			convertedById.put(
					transaction.getId(),
					resolveConvertedAmounts(transaction, filter.holdingId(), displayAsset, pageAssetByCodeUpper, rateMode, nowInstant)
			);
		}

		List<TransactionPageItemResponseDto> responseContent = content.stream()
				.map(dto -> toPageItemResponse(
						dto,
						convertedById.get(dto.id()),
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

		UUID holdingId = filter.holdingId();
		TransactionAmountRateMode rateMode = filter.rateMode() != null ? filter.rateMode() : TransactionAmountRateMode.NOW;

		List<AmountSortableTransaction> sortableItems = matchingTransactions.stream()
				.map(transaction -> new AmountSortableTransaction(
						transaction,
						toTransactionResponse(transaction, rootCategoryByCategoryId),
						resolveConvertedAmounts(transaction, holdingId, displayAsset, assetByCodeUpper, rateMode, nowInstant)
				))
				.sorted(amountSortComparator(amountSortOrder))
				.toList();

		List<TransactionResponseDto> orderedDtosWithScale = enrichWithAssetScale(
				sortableItems.stream().map(AmountSortableTransaction::dto).toList()
		);

		Map<UUID, ConvertedAmounts> convertedById = new HashMap<>();
		for (AmountSortableTransaction item : sortableItems) {
			convertedById.putIfAbsent(item.transaction().getId(), item.convertedAmounts());
		}

		List<TransactionPageItemResponseDto> orderedItems = orderedDtosWithScale.stream()
				.map(dto -> toPageItemResponse(
						dto,
						convertedById.get(dto.id()),
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
				.comparing((AmountSortableTransaction item) -> item.convertedAmounts().effectiveAmount(), convertedComparator)
				.thenComparing((left, right) -> right.transaction().getTransactionDate().compareTo(left.transaction().getTransactionDate()))
				.thenComparing((left, right) -> right.transaction().getId().compareTo(left.transaction().getId()));
	}

	private ConvertedAmounts resolveConvertedAmounts(
			Transaction transaction,
			UUID holdingId,
			Asset displayAsset,
			Map<String, Asset> assetByCodeUpper,
			TransactionAmountRateMode rateMode,
			Instant nowInstant
	) {
		if (displayAsset == null) {
			return new ConvertedAmounts(null, null, null);
		}

		Instant rateInstant = resolveRateInstant(transaction, rateMode, nowInstant);

		if (transaction.getTransactionType() != TransactionType.TRANSFER
				|| transaction.getSourceHolding() == null
				|| transaction.getTargetHolding() == null
				|| transaction.getSourceHolding().getAsset() == null
				|| transaction.getTargetHolding().getAsset() == null) {
			if (transaction.getCurrencyCode() == null) {
				return new ConvertedAmounts(null, null, null);
			}

			Long effective = convertToDisplayAmount(
					transaction.getAmount(),
					transaction.getCurrencyCode().toUpperCase(),
					displayAsset,
					assetByCodeUpper,
					rateInstant
			);
			return new ConvertedAmounts(effective, null, null);
		}

		String sourceAssetCode = transaction.getSourceHolding().getAsset().getCode().toUpperCase();
		String targetAssetCode = transaction.getTargetHolding().getAsset().getCode().toUpperCase();

		Long convertedSource = convertToDisplayAmount(
				transaction.getAmount(),
				sourceAssetCode,
				displayAsset,
				assetByCodeUpper,
				rateInstant
		);

		Long convertedTarget = convertToDisplayAmount(
				resolveTransferSettledAmount(transaction),
				targetAssetCode,
				displayAsset,
				assetByCodeUpper,
				rateInstant
		);

		Long effective = resolveEffectiveAmountForSorting(transaction, holdingId, convertedSource, convertedTarget);
		return new ConvertedAmounts(effective, convertedSource, convertedTarget);
	}

	private Long resolveEffectiveAmountForSorting(
			Transaction transaction,
			UUID holdingId,
			Long convertedSource,
			Long convertedTarget
	) {
		if (transaction.getTransactionType() != TransactionType.TRANSFER || holdingId == null) {
			return convertedSource;
		}

		if (transaction.getSourceHolding() != null && holdingId.equals(transaction.getSourceHolding().getId())) {
			return convertedSource;
		}
		if (transaction.getTargetHolding() != null && holdingId.equals(transaction.getTargetHolding().getId())) {
			return convertedTarget;
		}
		return convertedSource;
	}

	private Instant resolveRateInstant(Transaction transaction, TransactionAmountRateMode rateMode, Instant nowInstant) {
		TransactionAmountRateMode effectiveRateMode = rateMode != null ? rateMode : TransactionAmountRateMode.NOW;
		Instant rateInstant = effectiveRateMode == TransactionAmountRateMode.TRANSACTION_DATE
				? transaction.getTransactionDate()
				: nowInstant;
		return rateInstant != null ? rateInstant : nowInstant;
	}

	private Map<String, Asset> buildAssetByCodeUpper(List<Transaction> transactions) {
		Set<String> codesUpper = transactions.stream()
				.flatMap(transaction -> java.util.stream.Stream.of(
						transaction.getCurrencyCode(),
						transaction.getSourceHolding() != null && transaction.getSourceHolding().getAsset() != null
								? transaction.getSourceHolding().getAsset().getCode()
								: null,
						transaction.getTargetHolding() != null && transaction.getTargetHolding().getAsset() != null
								? transaction.getTargetHolding().getAsset().getCode()
								: null
				))
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
			ConvertedAmounts converted,
			String convertedInto,
			Integer convertedAssetScale
	) {
		Long convertedAmount = converted != null ? converted.effectiveAmount() : null;
		Long convertedSourceAmount = converted != null ? converted.sourceAmount() : null;
		Long convertedTargetAmount = converted != null ? converted.targetAmount() : null;

		return new TransactionPageItemResponseDto(
				dto.id(),
				dto.transactionType(),
				dto.status(),
				dto.holdingId(),
				dto.holdingName(),
				dto.sourceHoldingId(),
				dto.sourceHoldingName(),
				dto.sourceHoldingAssetCode(),
				dto.sourceHoldingAssetScale(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.targetHoldingAssetCode(),
				dto.targetHoldingAssetScale(),
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
				convertedSourceAmount,
				convertedTargetAmount,
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

		if (transaction.getTransactionType() == TransactionType.TRANSFER) {
			patchTransferFinancialFields(transaction, trackerId, request);
		} else if (transaction.getTransactionType() == TransactionType.BALANCE_ADJUSTMENT) {
			patchBalanceAdjustmentFields(transaction, request);
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
		return enrichWithAssetScale(normalizeDisplayedExchangeRate(transactionMapper.toResponse(transaction)));
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

		Holding oldHolding = transaction.getHolding();
		Holding newHolding = oldHolding;
		if (request.holdingId() != null) {
			newHolding = getHoldingOrThrow(request.holdingId());
			assertHoldingBelongsToTracker(newHolding, trackerId);
			assertHoldingActive(newHolding);
		}

		long oldEffect = resolveIncomeExpenseBalanceEffect(
				transaction.getTransactionType(),
				transaction.getAmount(),
				transaction.getFeeAmount(),
				transaction.getSettledAmount()
		);

		long newAmount = request.amount() != null ? request.amount() : transaction.getAmount();
		long newFeeAmount = request.feeAmount() != null ? request.feeAmount() : transaction.getFeeAmount();
		String newCurrencyCode = request.currencyCode() != null
				? request.currencyCode().toUpperCase()
				: transaction.getCurrencyCode();
		if (request.currencyCode() != null) {
			assertAssetCodeExists(newCurrencyCode);
		}

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
			newEffect = resolveIncomeExpenseBalanceEffect(transaction.getTransactionType(), newAmount, newFeeAmount, null);
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

	private void patchTransferFinancialFields(Transaction transaction, UUID trackerId, UpdateTransactionRequestDto request) {
		boolean financialPatchRequested = request.sourceHoldingId() != null
				|| request.targetHoldingId() != null
				|| request.amount() != null
				|| request.settledAmount() != null
				|| request.feeAmount() != null
				|| request.currencyCode() != null
				|| request.exchangeRate() != null;
		if (!financialPatchRequested) {
			return;
		}

		Holding oldSource = transaction.getSourceHolding();
		Holding oldTarget = transaction.getTargetHolding();

		Holding newSource = oldSource;
		if (request.sourceHoldingId() != null) {
			newSource = getHoldingOrThrow(request.sourceHoldingId());
			assertHoldingBelongsToTracker(newSource, trackerId);
			assertHoldingActive(newSource);
		}

		Holding newTarget = oldTarget;
		if (request.targetHoldingId() != null) {
			newTarget = getHoldingOrThrow(request.targetHoldingId());
			assertHoldingBelongsToTracker(newTarget, trackerId);
			assertHoldingActive(newTarget);
		}

		if (newSource.getId().equals(newTarget.getId())) {
			throw new OperationNotPermittedException("Source and target holdings must be different");
		}

		boolean sameAssetTransfer = newSource.getAsset().getCode().equalsIgnoreCase(newTarget.getAsset().getCode());

		if (request.currencyCode() != null) {
			assertAssetCodeExists(request.currencyCode().toUpperCase());
			if (!request.currencyCode().equalsIgnoreCase(newSource.getAsset().getCode())) {
				throw new OperationNotPermittedException("Currency code on transfer must match source holding asset code");
			}
		}

		if (sameAssetTransfer && request.exchangeRate() != null) {
			throw new OperationNotPermittedException("Exchange rate cannot be patched on same-asset transfers");
		}

		long oldSourceDeduction = isInternalSameAssetTransfer(transaction)
				? transaction.getAmount()
				: transaction.getAmount() + transaction.getFeeAmount();
		long oldTargetAddition = isInternalSameAssetTransfer(transaction)
				? resolveTransferSettledAmount(transaction)
				: (transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount());

		long newAmount = request.amount() != null ? request.amount() : transaction.getAmount();
		long newFee = request.feeAmount() != null ? request.feeAmount() : transaction.getFeeAmount();
		Long requestedSettled = request.settledAmount();

		long newSettled;
		BigDecimal effectiveNewExchangeRate;

		if (sameAssetTransfer) {
			if (requestedSettled != null) {
				newSettled = requestedSettled;
				newFee = Math.subtractExact(newAmount, newSettled);
			} else if (request.feeAmount() != null || request.amount() != null) {
				newSettled = Math.subtractExact(newAmount, newFee);
			} else {
				newSettled = resolveTransferSettledAmount(transaction);
			}
			effectiveNewExchangeRate = null;
		} else {
			BigDecimal effectiveRate = request.exchangeRate() != null ? request.exchangeRate() : transaction.getExchangeRate();
			if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
				throw new OperationNotPermittedException(
						"Exchange rate is required for cross-asset transfer patch (%s -> %s)"
								.formatted(newSource.getAsset().getCode(), newTarget.getAsset().getCode()));
			}

			if (requestedSettled != null) {
				newSettled = requestedSettled;
			} else if (request.amount() != null || request.exchangeRate() != null) {
				newSettled = BigDecimal.valueOf(newAmount)
						.multiply(effectiveRate)
						.setScale(0, RoundingMode.HALF_UP)
						.longValueExact();
			} else {
				newSettled = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();
			}
			effectiveNewExchangeRate = effectiveRate;
		}

		oldSource.setCurrentAmount(oldSource.getCurrentAmount() + oldSourceDeduction);
		oldTarget.setCurrentAmount(oldTarget.getCurrentAmount() - oldTargetAddition);

		long newSourceDeduction = sameAssetTransfer ? newAmount : Math.addExact(newAmount, newFee);
		long newTargetAddition = newSettled;

		newSource.setCurrentAmount(newSource.getCurrentAmount() - newSourceDeduction);
		newTarget.setCurrentAmount(newTarget.getCurrentAmount() + newTargetAddition);

		holdingRepository.save(oldSource);
		if (!oldTarget.getId().equals(oldSource.getId())) {
			holdingRepository.save(oldTarget);
		}
		if (!newSource.getId().equals(oldSource.getId()) && !newSource.getId().equals(oldTarget.getId())) {
			holdingRepository.save(newSource);
		}
		if (!newTarget.getId().equals(newSource.getId())
				&& !newTarget.getId().equals(oldSource.getId())
				&& !newTarget.getId().equals(oldTarget.getId())) {
			holdingRepository.save(newTarget);
		}

		transaction.setSourceHolding(newSource);
		transaction.setTargetHolding(newTarget);
		transaction.setAmount(newAmount);
		transaction.setSettledAmount(newSettled);
		transaction.setFeeAmount(newFee);
		transaction.setCurrencyCode(newSource.getAsset().getCode());
		transaction.setExchangeRate(effectiveNewExchangeRate);
	}

	private void patchBalanceAdjustmentFields(Transaction transaction, UpdateTransactionRequestDto request) {
		if (request.sourceHoldingId() != null
				|| request.targetHoldingId() != null
				|| request.exchangeRate() != null
				|| request.settledAmount() != null) {
			throw new OperationNotPermittedException(
					"Financial fields (holding/source/target, amount, settled, currency, exchange rate, fee) cannot be changed on a %s transaction"
							.formatted(transaction.getTransactionType()));
		}

		if (request.holdingId() != null
				&& (transaction.getHolding() == null || !request.holdingId().equals(transaction.getHolding().getId()))) {
			throw new OperationNotPermittedException("Holding cannot be changed on a %s transaction"
					.formatted(transaction.getTransactionType()));
		}

		if (request.currencyCode() != null
				&& !request.currencyCode().equalsIgnoreCase(transaction.getCurrencyCode())) {
			throw new OperationNotPermittedException("Currency cannot be changed on a %s transaction"
					.formatted(transaction.getTransactionType()));
		}

		if (request.feeAmount() != null
				&& request.feeAmount() != transaction.getFeeAmount()) {
			throw new OperationNotPermittedException("Fee amount cannot be changed on a %s transaction"
					.formatted(transaction.getTransactionType()));
		}

		if (request.amount() == null || request.amount().equals(transaction.getAmount())) {
			return;
		}

		Holding holding = transaction.getHolding();
		long oldSignedEffect = transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
				? transaction.getAmount()
				: -transaction.getAmount();
		long newSignedEffect = transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
				? request.amount()
				: -request.amount();

		holding.setCurrentAmount(holding.getCurrentAmount() - oldSignedEffect + newSignedEffect);
		holdingRepository.save(holding);
		transaction.setAmount(request.amount());
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
		TransactionResponseDto dto = normalizeDisplayedExchangeRate(transactionMapper.toResponse(transaction));

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
				dto.sourceHoldingAssetCode(),
				dto.sourceHoldingAssetScale(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.targetHoldingAssetCode(),
				dto.targetHoldingAssetScale(),
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
				dto.sourceHoldingAssetCode(),
				dto.sourceHoldingAssetScale(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.targetHoldingAssetCode(),
				dto.targetHoldingAssetScale(),
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
				.flatMap(transaction -> java.util.stream.Stream.of(
						transaction.getCurrencyCode(),
						transaction.getSourceHolding() != null && transaction.getSourceHolding().getAsset() != null
								? transaction.getSourceHolding().getAsset().getCode()
								: null,
						transaction.getTargetHolding() != null && transaction.getTargetHolding().getAsset() != null
								? transaction.getTargetHolding().getAsset().getCode()
								: null
				))
				.filter(Objects::nonNull)
				.map(String::toUpperCase)
				.collect(Collectors.toSet());

		Map<String, Asset> assetByCodeUpper = codesUpper.isEmpty()
				? Map.of()
				: assetRepository.findAllByCodeUpperIn(codesUpper).stream()
				.collect(Collectors.toMap(asset -> asset.getCode().toUpperCase(), Function.identity()));

		MutableConvertedTotals convertedTotals = new MutableConvertedTotals();
		convertedTotals.complete = displayAsset != null;
		Instant nowInstant = Instant.now();

		for (Transaction transaction : transactions) {
			if (transaction.getStatus() != TransactionStatus.COMPLETED) {
				continue;
			}

			Instant rateInstant = resolveRateInstant(transaction, rateMode, nowInstant);

			switch (transaction.getTransactionType()) {
				case INCOME -> applyTotalsDelta(
						totalsByAsset,
						convertedTotals,
						transaction.getCurrencyCode(),
						transaction.getAmount(),
						0,
						displayAsset,
						assetByCodeUpper,
						rateInstant
				);
				case EXPENSE -> applyTotalsDelta(
						totalsByAsset,
						convertedTotals,
						transaction.getCurrencyCode(),
						0,
						transaction.getAmount(),
						displayAsset,
						assetByCodeUpper,
						rateInstant
				);
				case BALANCE_ADJUSTMENT -> {
					if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION) {
						applyTotalsDelta(
								totalsByAsset,
								convertedTotals,
								transaction.getCurrencyCode(),
								transaction.getAmount(),
								0,
								displayAsset,
								assetByCodeUpper,
								rateInstant
						);
					} else if (transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.DEDUCTION) {
						applyTotalsDelta(
								totalsByAsset,
								convertedTotals,
								transaction.getCurrencyCode(),
								0,
								transaction.getAmount(),
								displayAsset,
								assetByCodeUpper,
								rateInstant
						);
					}
				}
				case TRANSFER -> {
					String sourceAssetCode = transaction.getSourceHolding() != null && transaction.getSourceHolding().getAsset() != null
							? transaction.getSourceHolding().getAsset().getCode()
							: transaction.getCurrencyCode();
					String targetAssetCode = transaction.getTargetHolding() != null && transaction.getTargetHolding().getAsset() != null
							? transaction.getTargetHolding().getAsset().getCode()
							: sourceAssetCode;
					boolean sameAssetTransfer = isSameAssetTransferByAssetCode(sourceAssetCode, targetAssetCode);

					if (holdingId == null) {
						if (sameAssetTransfer) {
							long feeEffect = resolveTransferEffectiveFee(transaction);
							if (feeEffect > 0) {
								applyTotalsDelta(totalsByAsset, convertedTotals, sourceAssetCode, 0, feeEffect, displayAsset, assetByCodeUpper, rateInstant);
							} else if (feeEffect < 0) {
								applyTotalsDelta(totalsByAsset, convertedTotals, sourceAssetCode, -feeEffect, 0, displayAsset, assetByCodeUpper, rateInstant);
							}
						} else {
							applyTotalsDelta(totalsByAsset, convertedTotals, sourceAssetCode, 0, transaction.getAmount(), displayAsset, assetByCodeUpper, rateInstant);
							applyTotalsDelta(totalsByAsset, convertedTotals, targetAssetCode, resolveTransferSettledAmount(transaction), 0, displayAsset, assetByCodeUpper, rateInstant);
						}
						break;
					}

					boolean outgoing = transaction.getSourceHolding() != null && holdingId.equals(transaction.getSourceHolding().getId());
					boolean incoming = transaction.getTargetHolding() != null && holdingId.equals(transaction.getTargetHolding().getId());

					if (outgoing) {
						applyTotalsDelta(totalsByAsset, convertedTotals, sourceAssetCode, 0, transaction.getAmount(), displayAsset, assetByCodeUpper, rateInstant);
					}
					if (incoming) {
						applyTotalsDelta(totalsByAsset, convertedTotals, targetAssetCode, resolveTransferSettledAmount(transaction), 0, displayAsset, assetByCodeUpper, rateInstant);
					}
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
				convertedTotals.complete ? convertedTotals.income : null,
				convertedTotals.complete ? convertedTotals.expense : null,
				convertedTotals.complete ? convertedTotals.income - convertedTotals.expense : null,
				displayAsset != null ? displayAsset.getCode() : null
		);

		return new TransactionTotalsDto(byAsset, converted);
	}

	private void applyTotalsDelta(
			Map<String, MutableAssetTotals> totalsByAsset,
			MutableConvertedTotals convertedTotals,
			String assetCode,
			long incomeDelta,
			long expenseDelta,
			Asset displayAsset,
			Map<String, Asset> assetByCodeUpper,
			Instant rateInstant
	) {
		if (incomeDelta == 0 && expenseDelta == 0) {
			return;
		}

		String assetCodeUpper = assetCode != null ? assetCode.toUpperCase() : null;
		if (assetCodeUpper != null) {
			MutableAssetTotals totals = totalsByAsset.computeIfAbsent(assetCodeUpper, ignored -> new MutableAssetTotals());
			totals.income += incomeDelta;
			totals.expense += expenseDelta;
		}

		if (!convertedTotals.complete) {
			return;
		}

		if (incomeDelta > 0) {
			Long converted = convertToDisplayAmount(incomeDelta, assetCodeUpper, displayAsset, assetByCodeUpper, rateInstant);
			if (converted == null) {
				convertedTotals.complete = false;
				return;
			}
			convertedTotals.income += converted;
		}

		if (expenseDelta > 0) {
			Long converted = convertToDisplayAmount(expenseDelta, assetCodeUpper, displayAsset, assetByCodeUpper, rateInstant);
			if (converted == null) {
				convertedTotals.complete = false;
				return;
			}
			convertedTotals.expense += converted;
		}
	}

	private boolean isSameAssetTransferByAssetCode(String sourceAssetCode, String targetAssetCode) {
		return sourceAssetCode != null
				&& sourceAssetCode.equalsIgnoreCase(targetAssetCode);
	}

	private boolean isInternalSameAssetTransfer(Transaction transaction) {
		if (transaction.getTransactionType() != TransactionType.TRANSFER) {
			return false;
		}
		if (transaction.getHolding() != null) {
			return false;
		}
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

		if (isInternalSameAssetTransfer(transaction) && transaction.getFeeAmount() != 0) {
			try {
				return Math.subtractExact(transaction.getAmount(), transaction.getFeeAmount());
			} catch (ArithmeticException ex) {
				log.warn("Overflow while deriving settledAmount for transfer '{}'", transaction.getId());
			}
		}

		return transaction.getAmount();
	}

	private long resolveTransferEffectiveFee(Transaction transaction) {
		long settledAmount = resolveTransferSettledAmount(transaction);
		try {
			return Math.subtractExact(transaction.getAmount(), settledAmount);
		} catch (ArithmeticException ex) {
			log.warn("Overflow while deriving fee effect for transfer '{}'", transaction.getId());
			return transaction.getFeeAmount();
		}
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
			balanceEffect = resolveIncomeExpenseBalanceEffect(type, request.amount(), feeAmount, null);
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
							long effect = resolveIncomeExpenseBalanceEffect(
									TransactionType.INCOME,
									transaction.getAmount(),
									transaction.getFeeAmount(),
									transaction.getSettledAmount()
							);
				holding.setCurrentAmount(holding.getCurrentAmount() - effect);
				holdingRepository.save(holding);
			}
			case EXPENSE -> {
				Holding holding = transaction.getHolding();
							long effect = resolveIncomeExpenseBalanceEffect(
									TransactionType.EXPENSE,
									transaction.getAmount(),
									transaction.getFeeAmount(),
									transaction.getSettledAmount()
							);
				holding.setCurrentAmount(holding.getCurrentAmount() + effect);
				holdingRepository.save(holding);
			}
			case TRANSFER -> {
				Holding source = transaction.getSourceHolding();
				Holding target = transaction.getTargetHolding();
				long sourceReversal;
				long targetReversal;

				if (isInternalSameAssetTransfer(transaction)) {
					sourceReversal = transaction.getAmount();
					targetReversal = resolveTransferSettledAmount(transaction);
				} else {
					sourceReversal = transaction.getAmount() + transaction.getFeeAmount();
					targetReversal = transaction.getSettledAmount() != null ? transaction.getSettledAmount() : transaction.getAmount();
				}

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

	private void assertAssetCodeExists(String assetCode) {
		if (!assetRepository.existsByCodeIgnoreCase(assetCode)) {
			throw new OperationNotPermittedException("Asset code '%s' does not exist".formatted(assetCode));
		}
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

	private long resolveIncomeExpenseBalanceEffect(TransactionType type, long amount, long feeAmount, Long settledAmount) {
		if (settledAmount != null) {
			return settledAmount;
		}
		if (type == TransactionType.EXPENSE) {
			return Math.addExact(amount, feeAmount);
		}
		return amount;
	}

	private static final class MutableAssetTotals {
		private long income;
		private long expense;
	}

	private static final class MutableConvertedTotals {
		private long income;
		private long expense;
		private boolean complete;
	}

	private record RootCategoryInfo(
			UUID id,
			String name
	) {
	}

	private record AmountSortableTransaction(
			Transaction transaction,
			TransactionResponseDto dto,
			ConvertedAmounts convertedAmounts
	) {
	}

	private record ConvertedAmounts(
			Long effectiveAmount,
			Long sourceAmount,
			Long targetAmount
	) {
	}

	private TransactionResponseDto normalizeDisplayedExchangeRate(TransactionResponseDto dto) {
		if (dto.exchangeRate() == null
				|| dto.transactionType() != TransactionType.TRANSFER
				|| dto.targetHoldingAssetScale() == null
				|| dto.exchangeRate().scale() == dto.targetHoldingAssetScale()) {
			return dto;
		}

		BigDecimal normalizedExchangeRate = dto.exchangeRate().setScale(dto.targetHoldingAssetScale(), RoundingMode.HALF_UP);

		return new TransactionResponseDto(
				dto.id(),
				dto.transactionType(),
				dto.status(),
				dto.holdingId(),
				dto.holdingName(),
				dto.sourceHoldingId(),
				dto.sourceHoldingName(),
				dto.sourceHoldingAssetCode(),
				dto.sourceHoldingAssetScale(),
				dto.targetHoldingId(),
				dto.targetHoldingName(),
				dto.targetHoldingAssetCode(),
				dto.targetHoldingAssetScale(),
				dto.categoryId(),
				dto.categoryName(),
				dto.rootCategoryId(),
				dto.rootCategoryName(),
				dto.amount(),
				dto.assetCode(),
				dto.assetScale(),
				normalizedExchangeRate,
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
}