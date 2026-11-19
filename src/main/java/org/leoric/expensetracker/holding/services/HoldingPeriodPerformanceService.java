package org.leoric.expensetracker.holding.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.holding.dto.HoldingPeriodPerformanceDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.TransactionValuationService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class HoldingPeriodPerformanceService {
	private final TransactionRepository transactionRepository;
	private final ExchangeRateService exchangeRateService;
	private final TransactionValuationService transactionValuationService;

	public HoldingPeriodPerformanceDto calculate(Holding holding, Asset displayAsset, HoldingSummaryResponseDto summary,
			Long convertedStart, Long convertedEnd) {
		Instant now = Instant.now();
		Instant end = summary.periodTo().isBefore(now) ? summary.periodTo() : now;
		boolean estimated = !holding.getAsset().getCode().equalsIgnoreCase(displayAsset.getCode());
		if (convertedStart == null || convertedEnd == null) {
			return new HoldingPeriodPerformanceDto(null, null, null, estimated, "Kurz pro začátek nebo konec období není dostupný.");
		}

		long income = 0;
		long expense = 0;
		Instant created = holding.getCreatedDate();
		if (created != null && !created.isBefore(summary.periodFrom()) && created.isBefore(end)
				&& holding.getInitialAmount() != 0) {
			Long value = holding.getOpeningMarketValue() != null && holding.getOpeningMarketValueAsset() != null
					? convert(holding.getOpeningMarketValue(), holding.getOpeningMarketValueAsset(), displayAsset, created)
					: convert(holding.getInitialAmount(), holding.getAsset(), displayAsset, created);
			if (value == null) {
				return unavailable(estimated);
			}
			if (value > 0) income = Math.addExact(income, value);
			else expense = Math.subtractExact(expense, value);
		}

		if (end.isAfter(summary.periodFrom())) {
			for (Transaction transaction : transactionRepository.findCompletedByHoldingAndDateRange(
					holding.getId(), summary.periodFrom(), end)) {
				if (holding.getCreatedDate() == null || transaction.getTransactionDate().isBefore(holding.getCreatedDate())) {
					return new HoldingPeriodPerformanceDto(null, null, null, estimated, "Transakce je datována před vznik pozice.");
				}
				if (transaction.getTransactionType() != org.leoric.expensetracker.transaction.models.constants.TransactionType.TRANSFER
						&& transactionValuationService.hasAmbiguousLegacyBalanceEffect(transaction, holding)) {
					return new HoldingPeriodPerformanceDto(null, null, null, estimated,
							"Starší transakce má nejasný přepočet nebo poplatek; zkontrolujte a upravte ji.");
				}
				Long signedValue = transactionValue(transaction, holding, displayAsset);
				if (signedValue == null) {
					return unavailable(estimated);
				}
				if (signedValue > 0) income = Math.addExact(income, signedValue);
				else expense = Math.subtractExact(expense, signedValue);
			}
		}
		long result = Math.addExact(Math.subtractExact(Math.subtractExact(convertedEnd, convertedStart), income), expense);
		return new HoldingPeriodPerformanceDto(income, expense, result, estimated, null);
	}

	private Long transactionValue(Transaction transaction, Holding holding, Asset displayAsset) {
		long signedAmount;
		switch (transaction.getTransactionType()) {
			case INCOME -> {
				if (transactionValuationService.hasAmbiguousLegacyBalanceEffect(transaction, holding)) return null;
				long fee = transaction.getSettledFeeAmount() != null ? transaction.getSettledFeeAmount() : transaction.getFeeAmount();
				if (fee < 0) return null;
				signedAmount = Math.addExact(balanceEffect(transaction), fee);
			}
			case EXPENSE, TEST -> {
				if (transactionValuationService.hasAmbiguousLegacyBalanceEffect(transaction, holding)) return null;
				long effect = balanceEffect(transaction);
				long fee = transaction.getSettledFeeAmount() != null ? transaction.getSettledFeeAmount() : transaction.getFeeAmount();
				if (fee < 0 || fee > effect) return null;
				signedAmount = Math.negateExact(Math.subtractExact(effect, fee));
			}
			case BALANCE_ADJUSTMENT -> signedAmount = transaction.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
					? transaction.getAmount() : Math.negateExact(transaction.getAmount());
			case TRANSFER -> {
				boolean outgoing = transaction.getSourceHolding() != null
						&& transaction.getSourceHolding().getId().equals(holding.getId());
				Holding source = transaction.getSourceHolding();
				Holding target = transaction.getTargetHolding();
				if (source == null || target == null || source.getAsset() == null || target.getAsset() == null) {
					return null;
				}
				boolean sameAsset = source.getAsset().getCode().equalsIgnoreCase(target.getAsset().getCode());
				long received = transaction.getSettledAmount() != null
						? transaction.getSettledAmount()
						: sameAsset ? Math.subtractExact(transaction.getAmount(), transaction.getFeeAmount()) : -1;
				if (received < 0) return null;
				if (!sameAsset && source.getAsset().getCode().equalsIgnoreCase(displayAsset.getCode())) {
					return outgoing ? Math.negateExact(transaction.getAmount()) : transaction.getAmount();
				}
				if (!sameAsset && target.getAsset().getCode().equalsIgnoreCase(displayAsset.getCode())) {
					return outgoing ? Math.negateExact(received) : received;
				}
				if (!sameAsset) {
					Long value = transactionValuationService.convert(transaction, received, target.getAsset(), displayAsset);
					return value == null ? null : outgoing ? Math.negateExact(value) : value;
				}
				signedAmount = outgoing ? Math.negateExact(received) : received;
			}
			default -> throw new IllegalStateException("Unsupported transaction type: " + transaction.getTransactionType());
		}
		return transactionValuationService.convert(transaction, signedAmount, holding.getAsset(), displayAsset);
	}

	private long balanceEffect(Transaction transaction) {
		if (transaction.getSettledAmount() != null) return transaction.getSettledAmount();
		return switch (transaction.getTransactionType()) {
			case INCOME -> transaction.getAmount();
			case EXPENSE, TEST -> Math.addExact(transaction.getAmount(), transaction.getFeeAmount());
			default -> throw new IllegalArgumentException("Expected income or expense");
		};
	}

	private Long convert(long amount, Asset from, Asset to, Instant at) {
		if (amount == 0) return 0L;
		if (from.getCode().equalsIgnoreCase(to.getCode())) return amount;
		return exchangeRateService.convertAmount(amount, from, to, at);
	}

	private HoldingPeriodPerformanceDto unavailable(boolean estimated) {
		return new HoldingPeriodPerformanceDto(null, null, null, estimated, "Kurz nebo částka pro pohyb v období není dostupná.");
	}
}
