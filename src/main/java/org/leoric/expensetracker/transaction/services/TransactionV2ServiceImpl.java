package org.leoric.expensetracker.transaction.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeAmountLessThanFeeException;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeSameAssetException;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeSettledAmountRequiredException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.handler.exceptions.TransferAmountComputationException;
import org.leoric.expensetracker.handler.exceptions.TransferAmountInputMissingException;
import org.leoric.expensetracker.handler.exceptions.TransferExchangeRateInvalidException;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteRequestDto;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2ResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.TransferAmountCalculationMode;
import org.leoric.expensetracker.transaction.dto.TransactionV2OperationType;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionV2Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionV2ServiceImpl implements TransactionV2Service {

	private final TransactionRepository transactionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final HoldingRepository holdingRepository;
	private final ExchangeRateService exchangeRateService;

	@Override
	@Transactional
	public CreateWalletTransferV2ResponseDto createWalletTransfer(User currentUser, UUID trackerId, CreateWalletTransferV2RequestDto request) {
		return createWalletTransferInternal(currentUser, trackerId, request);
	}

	@Override
	@Transactional
	public CreateAssetExchangeV2ResponseDto createAssetExchange(User currentUser, UUID trackerId, CreateAssetExchangeV2RequestDto request) {
		return createAssetExchangeInternal(currentUser, trackerId, request);
	}

	@Override
	@Transactional(readOnly = true)
	public AssetExchangeRateQuoteResponseDto assetExchangeRateQuote(User currentUser, UUID trackerId, AssetExchangeRateQuoteRequestDto request) {
		UUID sourceHoldingId = request.sourceHoldingId();
		UUID targetHoldingId = request.targetHoldingId();

		if (sourceHoldingId == null) {
			throw new OperationNotPermittedException("Source holding is required");
		}
		if (sourceHoldingId.equals(targetHoldingId)) {
			throw new OperationNotPermittedException("Source and target holdings must be different");
		}

		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Holding source = getHoldingOrThrow(sourceHoldingId);

		assertHoldingBelongsToTracker(source, trackerId);
		assertHoldingActive(source);

		Asset targetAsset;
		UUID resolvedTargetHoldingId = null;
		if (targetHoldingId != null) {
			Holding target = getHoldingOrThrow(targetHoldingId);
			assertHoldingBelongsToTracker(target, trackerId);
			assertHoldingActive(target);
			targetAsset = target.getAsset();
			resolvedTargetHoldingId = target.getId();
		} else {
			targetAsset = tracker.getPreferredDisplayAsset();
			if (targetAsset == null) {
				throw new OperationNotPermittedException("Target holding is missing and tracker has no preferredDisplayAsset set");
			}
		}

		LocalDate rateDate = LocalDate.now(ZoneOffset.UTC);
		BigDecimal rate = exchangeRateService.getRate(source.getAsset(), targetAsset, rateDate);
		if (rate == null) {
			throw new OperationNotPermittedException("Exchange rate is unavailable for %s/%s on %s"
					.formatted(source.getAsset().getCode(), targetAsset.getCode(), rateDate));
		}

		return new AssetExchangeRateQuoteResponseDto(
				source.getId(),
				source.getAsset().getCode(),
				resolvedTargetHoldingId,
				targetAsset.getCode(),
				rateDate,
				rate
		);
	}

	private CreateWalletTransferV2ResponseDto createWalletTransferInternal(
			User currentUser,
			UUID trackerId,
			CreateWalletTransferV2RequestDto request
	) {
		UUID sourceHoldingId = request.sourceHoldingId();
		UUID targetHoldingId = request.targetHoldingId();
		Long amountInput = request.amount();
		Long settledInput = request.settledAmount();
		Instant transactionDateInput = request.transactionDate();
		String description = request.description();
		String note = request.note();
		String externalRef = request.externalRef();

		if (sourceHoldingId == null || targetHoldingId == null) {
			throw new OperationNotPermittedException("Source and target holdings are required");
		}
		if (sourceHoldingId.equals(targetHoldingId)) {
			throw new OperationNotPermittedException("Source and target holdings must be different");
		}

		ResolvedTransferAmounts resolved = resolveWalletTransferAmounts(amountInput, settledInput);

		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Holding source = getHoldingOrThrow(sourceHoldingId);
		Holding target = getHoldingOrThrow(targetHoldingId);

		assertHoldingBelongsToTracker(source, trackerId);
		assertHoldingBelongsToTracker(target, trackerId);
		assertHoldingActive(source);
		assertHoldingActive(target);
		if (!source.getAsset().getCode().equalsIgnoreCase(target.getAsset().getCode())) {
			throw new OperationNotPermittedException("Wallet transfer requires source and target holding with the same asset");
		}

		long sourceDeduction = resolved.amount();
		long targetAddition = resolved.settledAmount();

		source.setCurrentAmount(source.getCurrentAmount() - sourceDeduction);
		target.setCurrentAmount(target.getCurrentAmount() + targetAddition);
		holdingRepository.save(source);
		holdingRepository.save(target);

		String assetCode = source.getAsset().getCode();
		Instant transactionDate = transactionDateInput != null ? transactionDateInput : Instant.now();

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(resolved.amount())
				.currencyCode(assetCode)
				.exchangeRate(null)
				.feeAmount(resolved.feeAmount())
				.settledAmount(resolved.settledAmount())
				.transactionDate(transactionDate)
				.description(description)
				.note(note)
				.externalRef(externalRef)
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created {} V2 transfer '{}' in tracker '{}'", currentUser.getEmail(), TransactionV2OperationType.WALLET_TRANSFER, transaction.getId(), tracker.getName());

		return new CreateWalletTransferV2ResponseDto(
				transaction.getId(),
				TransactionV2OperationType.WALLET_TRANSFER,
				resolved.calculationMode(),
				resolved.amount(),
				resolved.settledAmount(),
				resolved.feeAmount(),
				sourceDeduction,
				targetAddition,
				resolved.feeOverridden(),
				source.getId(),
				target.getId(),
				source.getAsset().getCode(),
				source.getAsset().getScale(),
				target.getAsset().getCode(),
				target.getAsset().getScale(),
				transactionDate
		);
	}

	private ResolvedTransferAmounts resolveWalletTransferAmounts(Long amountInput, Long settledInput) {
		if (amountInput == null && settledInput == null) {
			throw new TransferAmountInputMissingException("Amount or settledAmount must be provided for wallet transfer");
		}

		long amount = amountInput != null ? amountInput : settledInput;
		long settled = settledInput != null ? settledInput : amount;
		long computedFee = safeSubtract(amount, settled, "fee amount");

		return new ResolvedTransferAmounts(
				amount,
				settled,
				computedFee,
				amountInput != null && settledInput != null
						? TransferAmountCalculationMode.AMOUNT_AND_SETTLED
						: amountInput != null
						? TransferAmountCalculationMode.AMOUNT_ONLY_DEFAULTED
						: TransferAmountCalculationMode.SETTLED_ONLY_DEFAULTED,
				false
		);
	}

	private CreateAssetExchangeV2ResponseDto createAssetExchangeInternal(
			User currentUser,
			UUID trackerId,
			CreateAssetExchangeV2RequestDto request
	) {
		UUID sourceHoldingId = request.sourceHoldingId();
		UUID targetHoldingId = request.targetHoldingId();
		Long amountInput = request.amount();
		Long feeInput = request.feeAmount();
		Long settledInput = request.settledAmount();
		BigDecimal exchangeRate = request.exchangeRate();
		Instant transactionDateInput = request.transactionDate();
		String description = request.description();
		String note = request.note();
		String externalRef = request.externalRef();

		if (sourceHoldingId == null || targetHoldingId == null) {
			throw new OperationNotPermittedException("Source and target holdings are required");
		}
		if (sourceHoldingId.equals(targetHoldingId)) {
			throw new OperationNotPermittedException("Source and target holdings must be different");
		}

		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Holding source = getHoldingOrThrow(sourceHoldingId);
		Holding target = getHoldingOrThrow(targetHoldingId);

		assertHoldingBelongsToTracker(source, trackerId);
		assertHoldingBelongsToTracker(target, trackerId);
		assertHoldingActive(source);
		assertHoldingActive(target);

		if (source.getAsset().getCode().equalsIgnoreCase(target.getAsset().getCode())) {
			throw new AssetExchangeSameAssetException("Asset exchange requires different source and target assets");
		}

		ResolvedAssetExchangeAmounts resolved = resolveAssetExchangeAmounts(
				amountInput,
				feeInput,
				settledInput,
				exchangeRate,
				source.getAsset().getScale(),
				target.getAsset().getScale()
		);

		long sourceDeduction = resolved.amount();
		long targetAddition = resolved.settledAmount();

		source.setCurrentAmount(source.getCurrentAmount() - sourceDeduction);
		target.setCurrentAmount(target.getCurrentAmount() + targetAddition);
		holdingRepository.save(source);
		holdingRepository.save(target);

		String assetCode = source.getAsset().getCode();
		Instant transactionDate = transactionDateInput != null ? transactionDateInput : Instant.now();

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(resolved.amount())
				.currencyCode(assetCode)
				.exchangeRate(resolved.exchangeRate())
				.feeAmount(resolved.feeAmount())
				.settledAmount(resolved.settledAmount())
				.transactionDate(transactionDate)
				.description(description)
				.note(note)
				.externalRef(externalRef)
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created {} V2 transfer '{}' with holding assets exchange in tracker '{}'", currentUser.getEmail(), TransactionV2OperationType.ASSET_EXCHANGE, transaction.getId(), tracker.getName());

		return new CreateAssetExchangeV2ResponseDto(
				transaction.getId(),
				TransactionV2OperationType.ASSET_EXCHANGE,
				resolved.calculationMode(),
				resolved.amount(),
				resolved.settledAmount(),
				resolved.feeAmount(),
				sourceDeduction,
				targetAddition,
				resolved.feeOverridden(),
				source.getId(),
				source.getAccount().getName(),
				target.getId(),
				target.getAccount().getName(),
				source.getAsset().getCode(),
				source.getAsset().getScale(),
				target.getAsset().getCode(),
				target.getAsset().getScale(),
				assetCode,
				resolved.exchangeRate(),
				transactionDate
		);
	}

	private ResolvedAssetExchangeAmounts resolveAssetExchangeAmounts(
			Long amountInput,
			Long feeInput,
			Long settledInput,
			BigDecimal exchangeRateInput,
			int sourceAssetScale,
			int targetAssetScale
	) {
		if (amountInput == null || amountInput <= 0) {
			throw new TransferAmountInputMissingException("Asset exchange requires a positive amount input");
		}

		long amount = amountInput;
		long fee = feeInput != null ? feeInput : 0L;

		if (amount < fee) {
			throw new AssetExchangeAmountLessThanFeeException(
					"Asset exchange amount (%d) must be greater than or equal to feeAmount (%d)"
							.formatted(amount, fee));
		}

		long principal = safeSubtract(amount, fee, "principal amount");
		boolean hasSettled = settledInput != null;
		boolean hasRate = exchangeRateInput != null;

		if (!hasSettled && !hasRate) {
			throw new AssetExchangeSettledAmountRequiredException(
					"Asset exchange requires settledAmount when exchangeRate is missing");
		}

		if (hasRate && exchangeRateInput.compareTo(BigDecimal.ZERO) <= 0) {
			throw new TransferExchangeRateInvalidException("Exchange rate must be positive when provided");
		}

		long settled;
		if (hasSettled) {
			settled = settledInput;
		} else {
			BigDecimal targetMinorPerSourceMinor = minorUnitScaleFactor(targetAssetScale - sourceAssetScale);
			settled = BigDecimal.valueOf(principal)
					.multiply(exchangeRateInput)
					.multiply(targetMinorPerSourceMinor)
					.setScale(0, RoundingMode.HALF_UP)
					.longValueExact();
		}

		BigDecimal effectiveExchangeRate = exchangeRateInput;
		if (effectiveExchangeRate == null) {
			if (principal == 0L) {
				throw new AssetExchangeSettledAmountRequiredException(
						"Asset exchange cannot derive exchangeRate when principal amount is zero");
			}
			BigDecimal sourceMajorPerTargetMajor = minorUnitScaleFactor(sourceAssetScale - targetAssetScale);
			effectiveExchangeRate = BigDecimal.valueOf(settled)
					.divide(BigDecimal.valueOf(principal), 16, RoundingMode.HALF_UP)
					.multiply(sourceMajorPerTargetMajor)
					.setScale(targetAssetScale, RoundingMode.HALF_UP);
		}

		effectiveExchangeRate = effectiveExchangeRate.setScale(targetAssetScale, RoundingMode.HALF_UP);

		TransferAmountCalculationMode mode;
		if (hasSettled && hasRate) {
			mode = TransferAmountCalculationMode.ALL_FIELDS_RECONCILED;
		} else if (hasSettled) {
			mode = TransferAmountCalculationMode.AMOUNT_AND_SETTLED;
		} else {
			mode = TransferAmountCalculationMode.AMOUNT_AND_FEE;
		}

		return new ResolvedAssetExchangeAmounts(
				amount,
				settled,
				fee,
				mode,
				false,
				effectiveExchangeRate
		);
	}

	private BigDecimal minorUnitScaleFactor(int exponent) {
		if (exponent == 0) {
			return BigDecimal.ONE;
		}
		if (exponent > 0) {
			return BigDecimal.TEN.pow(exponent);
		}
		return BigDecimal.ONE.divide(BigDecimal.TEN.pow(-exponent), 16, RoundingMode.HALF_UP);
	}

	private long safeSubtract(long left, long right, String targetField) {
		try {
			return Math.subtractExact(left, right);
		} catch (ArithmeticException ex) {
			throw new TransferAmountComputationException("Overflow while computing " + targetField);
		}
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Holding getHoldingOrThrow(UUID holdingId) {
		return holdingRepository.findById(holdingId)
				.orElseThrow(() -> new EntityNotFoundException("Holding not found"));
	}

	private void assertHoldingBelongsToTracker(Holding holding, UUID trackerId) {
		if (!holding.getAccount().getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Holding not found in this expense tracker");
		}
	}

	private void assertHoldingActive(Holding holding) {
		if (!holding.isActive()) {
			throw new OperationNotPermittedException("Holding '%s/%s' is deactivated".formatted(
					holding.getAccount().getName(), holding.getAsset().getCode()));
		}
	}

	private record ResolvedTransferAmounts(
			long amount,
			long settledAmount,
			long feeAmount,
			TransferAmountCalculationMode calculationMode,
			boolean feeOverridden
	) {
	}

	private record ResolvedAssetExchangeAmounts(
			long amount,
			long settledAmount,
			long feeAmount,
			TransferAmountCalculationMode calculationMode,
			boolean feeOverridden,
			BigDecimal exchangeRate
	) {
	}
}