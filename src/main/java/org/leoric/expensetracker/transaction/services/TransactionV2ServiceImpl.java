package org.leoric.expensetracker.transaction.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeSameAssetException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.handler.exceptions.TransferAmountComputationException;
import org.leoric.expensetracker.handler.exceptions.TransferAmountInputMissingException;
import org.leoric.expensetracker.handler.exceptions.TransferExchangeRateInvalidException;
import org.leoric.expensetracker.handler.exceptions.TransferFeeOnlyInputException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.TransferAmountCalculationMode;
import org.leoric.expensetracker.transaction.dto.TransactionV2OperationType;
import org.leoric.expensetracker.transaction.dto.TransactionV2ResponseDto;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionV2Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionV2ServiceImpl implements TransactionV2Service {

	private final TransactionRepository transactionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final HoldingRepository holdingRepository;

	@Override
	@Transactional
	public CreateWalletTransferV2ResponseDto createWalletTransfer(User currentUser, UUID trackerId, CreateWalletTransferV2RequestDto request) {
		return createWalletTransferInternal(currentUser, trackerId, request);
	}

	@Override
	@Transactional
	public TransactionV2ResponseDto createAssetExchange(User currentUser, UUID trackerId, CreateAssetExchangeV2RequestDto request) {
		return createAssetExchangeInternal(currentUser, trackerId, request);
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

		String currencyCode = source.getAsset().getCode();
		Instant transactionDate = transactionDateInput != null ? transactionDateInput : Instant.now();

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(resolved.amount())
				.currencyCode(currencyCode)
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

	private TransactionV2ResponseDto createAssetExchangeInternal(
			User currentUser,
			UUID trackerId,
			CreateAssetExchangeV2RequestDto request
	) {
		UUID sourceHoldingId = request.sourceHoldingId();
		UUID targetHoldingId = request.targetHoldingId();
		Long amountInput = request.amount();
		Long settledInput = request.settledAmount();
		Long feeInput = request.feeAmount();
		String currencyCodeInput = request.currencyCode();
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
		if (exchangeRate != null && exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
			throw new TransferExchangeRateInvalidException("Exchange rate must be positive when provided");
		}

		ResolvedTransferAmounts resolved = resolveTransferAmounts(amountInput, settledInput, feeInput);

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

		long sourceDeduction = safeAdd(resolved.amount(), resolved.feeAmount(), "source deduction");
		long targetAddition = resolved.settledAmount();

		source.setCurrentAmount(source.getCurrentAmount() - sourceDeduction);
		target.setCurrentAmount(target.getCurrentAmount() + targetAddition);
		holdingRepository.save(source);
		holdingRepository.save(target);

		String currencyCode = normalizeCurrencyCode(currencyCodeInput, source.getAsset().getCode());
		Instant transactionDate = transactionDateInput != null ? transactionDateInput : Instant.now();

		Transaction transaction = Transaction.builder()
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(resolved.amount())
				.currencyCode(currencyCode)
				.exchangeRate(exchangeRate)
				.feeAmount(resolved.feeAmount())
				.settledAmount(resolved.settledAmount())
				.transactionDate(transactionDate)
				.description(description)
				.note(note)
				.externalRef(externalRef)
				.build();

		transaction = transactionRepository.save(transaction);
		log.info("User {} created {} V2 transfer '{}' in tracker '{}'", currentUser.getEmail(), TransactionV2OperationType.ASSET_EXCHANGE, transaction.getId(), tracker.getName());

		return new TransactionV2ResponseDto(
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
				target.getId(),
				source.getAsset().getCode(),
				target.getAsset().getCode(),
				currencyCode,
				exchangeRate,
				transactionDate
		);
	}

	private ResolvedTransferAmounts resolveTransferAmounts(Long amountInput, Long settledInput, Long feeInput) {
		boolean hasAmount = amountInput != null;
		boolean hasSettled = settledInput != null;
		boolean hasFee = feeInput != null;

		if (!hasAmount && !hasSettled && !hasFee) {
			throw new TransferAmountInputMissingException("At least one of amount, settledAmount or feeAmount must be provided");
		}
		if (!hasAmount && !hasSettled) {
			throw new TransferFeeOnlyInputException("Fee-only input is not supported. Provide amount or settledAmount");
		}

		if (hasAmount && hasSettled) {
			long amount = amountInput;
			long settled = settledInput;
			long computedFee = safeSubtract(amount, settled, "fee amount");
			boolean feeOverridden = hasFee && feeInput != computedFee;
			return new ResolvedTransferAmounts(
					amount,
					settled,
					computedFee,
					hasFee ? TransferAmountCalculationMode.ALL_FIELDS_RECONCILED : TransferAmountCalculationMode.AMOUNT_AND_SETTLED,
					feeOverridden
			);
		}

		if (hasAmount && hasFee) {
			long amount = amountInput;
			long fee = feeInput;
			long settled = safeSubtract(amount, fee, "settled amount");
			return new ResolvedTransferAmounts(amount, settled, fee, TransferAmountCalculationMode.AMOUNT_AND_FEE, false);
		}

		if (hasSettled && hasFee) {
			long settled = settledInput;
			long fee = feeInput;
			long amount = safeAdd(settled, fee, "amount");
			return new ResolvedTransferAmounts(amount, settled, fee, TransferAmountCalculationMode.SETTLED_AND_FEE, false);
		}

		if (hasAmount) {
			long amount = amountInput;
			return new ResolvedTransferAmounts(amount, amount, 0L, TransferAmountCalculationMode.AMOUNT_ONLY_DEFAULTED, false);
		}

		long settled = settledInput;
		return new ResolvedTransferAmounts(settled, settled, 0L, TransferAmountCalculationMode.SETTLED_ONLY_DEFAULTED, false);
	}

	private long safeAdd(long left, long right, String targetField) {
		try {
			return Math.addExact(left, right);
		} catch (ArithmeticException ex) {
			throw new TransferAmountComputationException("Overflow while computing " + targetField);
		}
	}

	private long safeSubtract(long left, long right, String targetField) {
		try {
			return Math.subtractExact(left, right);
		} catch (ArithmeticException ex) {
			throw new TransferAmountComputationException("Overflow while computing " + targetField);
		}
	}

	private String normalizeCurrencyCode(String currencyCodeInput, String fallbackCode) {
		if (currencyCodeInput == null || currencyCodeInput.isBlank()) {
			return fallbackCode;
		}
		return currencyCodeInput.toUpperCase();
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
}