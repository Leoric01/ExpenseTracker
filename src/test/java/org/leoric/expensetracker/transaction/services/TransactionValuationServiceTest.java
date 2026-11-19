package org.leoric.expensetracker.transaction.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.exchangerate.services.ExchangeRateMetadata;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionValuationServiceTest {
	@Mock ExchangeRateService rates;

	@Test
	void savedQuoteIsStableAndRespectsMinorUnitScales() {
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Instant at = Instant.parse("2025-01-02T12:00:00Z");
		Transaction transaction = Transaction.builder().transactionType(TransactionType.INCOME)
				.expenseTracker(ExpenseTracker.builder().preferredDisplayAsset(czk).build())
				.holding(Holding.builder().asset(btc).build()).transactionDate(at).build();
		when(rates.getRate(btc, czk, at.atZone(ZoneOffset.UTC).toLocalDate()))
				.thenReturn(new BigDecimal("2000000"));
		Instant fetched = Instant.parse("2025-01-03T05:00:00Z");
		when(rates.getRateMetadata(btc, czk, at.atZone(ZoneOffset.UTC).toLocalDate()))
				.thenReturn(new ExchangeRateMetadata(MarketDataSource.COINGECKO,
						at.atZone(ZoneOffset.UTC).toLocalDate(), fetched, true));
		TransactionValuationService service = new TransactionValuationService(rates);

		service.capture(transaction);
		service.capture(transaction);

		assertThat(transaction.getMarketRateObservedAt()).isNotNull();
		assertThat(transaction.getMarketRateSource()).isEqualTo(MarketDataSource.COINGECKO);
		assertThat(transaction.getMarketRateFetchedAt()).isEqualTo(fetched);
		assertThat(transaction.getMarketRateDailyHistorical()).isTrue();
		assertThat(service.convert(transaction, 100_000, btc, czk)).isEqualTo(200_000);
		verify(rates).getRate(btc, czk, at.atZone(ZoneOffset.UTC).toLocalDate());
	}

	@Test
	void changedTransactionDateDoesNotReuseOldSnapshot() {
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Instant oldDate = Instant.parse("2025-01-02T12:00:00Z");
		Instant newDate = Instant.parse("2025-01-03T12:00:00Z");
		Transaction transaction = Transaction.builder().transactionType(TransactionType.INCOME)
				.marketRate(new BigDecimal("2000000")).marketRateBaseAsset(btc).marketRateQuoteAsset(czk)
				.marketRateTransactionDate(oldDate).transactionDate(newDate).build();
		when(rates.convertAmount(100_000L, btc, czk, newDate)).thenReturn(210_000L);

		assertThat(new TransactionValuationService(rates).convert(transaction, 100_000, btc, czk))
				.isEqualTo(210_000);
	}

	@Test
	void savedQuoteOverflowIsUnavailableRatherThanAClippedValue() {
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Instant at = Instant.parse("2025-01-02T12:00:00Z");
		Transaction transaction = Transaction.builder()
				.marketRate(new BigDecimal("999999999999")).marketRateBaseAsset(btc).marketRateQuoteAsset(czk)
				.marketRateTransactionDate(at).transactionDate(at).build();

		assertThat(new TransactionValuationService(rates).convert(transaction, Long.MAX_VALUE, btc, czk)).isNull();
	}
}
