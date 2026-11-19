package org.leoric.expensetracker.transaction.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.exchangerate.services.ExchangeRateMetadata;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionValuationService {
	private final ExchangeRateService rates;

	public void capture(Transaction transaction) {
		Asset quote = transaction.getExpenseTracker().getPreferredDisplayAsset();
		Asset base = valuationBase(transaction, quote);
		Instant at = transaction.getTransactionDate();
		if (base == null || quote == null || at == null || at.isAfter(Instant.now())) return;
		if (transaction.getMarketRate() != null
				&& same(base, transaction.getMarketRateBaseAsset())
				&& same(quote, transaction.getMarketRateQuoteAsset())
				&& at.equals(transaction.getMarketRateTransactionDate())) return;

		transaction.setMarketRate(null);
		transaction.setMarketRateBaseAsset(null);
		transaction.setMarketRateQuoteAsset(null);
		transaction.setMarketRateObservedAt(null);
		transaction.setMarketRateTransactionDate(null);
		transaction.setMarketRateSource(null);
		transaction.setMarketRateFetchedAt(null);
		transaction.setMarketRateDailyHistorical(null);
		BigDecimal rate = rates.getRate(base, quote, at.atZone(ZoneOffset.UTC).toLocalDate());
		if (rate == null || rate.signum() <= 0 || rate.setScale(12, RoundingMode.HALF_UP).signum() == 0) {
			log.warn("Market rate unavailable for transaction valuation {}/{} at {}", base.getCode(), quote.getCode(), at);
			return;
		}
		transaction.setMarketRate(rate);
		transaction.setMarketRateBaseAsset(base);
		transaction.setMarketRateQuoteAsset(quote);
		transaction.setMarketRateObservedAt(Instant.now());
		transaction.setMarketRateTransactionDate(at);
		ExchangeRateMetadata metadata = rates.getRateMetadata(base, quote, at.atZone(ZoneOffset.UTC).toLocalDate());
		if (metadata != null) {
			transaction.setMarketRateSource(metadata.source());
			transaction.setMarketRateFetchedAt(metadata.fetchedAt());
			transaction.setMarketRateDailyHistorical(metadata.dailyHistorical());
		}
	}

	public Long convert(Transaction transaction, long amount, Asset from, Asset to) {
		if (amount == 0) return 0L;
		if (same(from, to)) return amount;
		if (transaction.getMarketRate() != null && same(from, transaction.getMarketRateBaseAsset())
				&& same(to, transaction.getMarketRateQuoteAsset())
				&& transaction.getTransactionDate().equals(transaction.getMarketRateTransactionDate())) {
			try {
				return BigDecimal.valueOf(amount).multiply(transaction.getMarketRate())
						.movePointRight(to.getScale() - from.getScale())
						.setScale(0, RoundingMode.HALF_UP).longValueExact();
			} catch (ArithmeticException ex) {
				log.warn("Transaction valuation exceeds supported range for transaction {}", transaction.getId());
				return null;
			}
		}
		return rates.convertAmount(amount, from, to, transaction.getTransactionDate());
	}

	public boolean hasAmbiguousLegacyBalanceEffect(Transaction transaction, Holding holding) {
		if (transaction.getBalanceEffectVersion() != null || holding == null || holding.getAsset() == null) return false;
		boolean crossCurrency = transaction.getCurrencyCode() != null
				&& !transaction.getCurrencyCode().equalsIgnoreCase(holding.getAsset().getCode());
		return crossCurrency || (transaction.getTransactionType() == TransactionType.INCOME && transaction.getFeeAmount() != 0);
	}

	public Asset valuationBase(Transaction transaction, Asset quote) {
		if (quote == null) return null;
		if (transaction.getTransactionType() != TransactionType.TRANSFER) {
			return transaction.getHolding() != null ? transaction.getHolding().getAsset() : null;
		}
		Holding source = transaction.getSourceHolding();
		Holding target = transaction.getTargetHolding();
		if (source == null || target == null || source.getAsset() == null || target.getAsset() == null) return null;
		if (same(source.getAsset(), target.getAsset())) return source.getAsset();
		if (same(source.getAsset(), quote) || same(target.getAsset(), quote)) return null;
		return target.getAsset();
	}

	private boolean same(Asset left, Asset right) {
		return left != null && right != null && left.getCode().equalsIgnoreCase(right.getCode());
	}
}
