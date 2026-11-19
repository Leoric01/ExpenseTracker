package org.leoric.expensetracker.exchangerate.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.exchangerate.clients.CoinGeckoClient;
import org.leoric.expensetracker.exchangerate.clients.FrankfurterClient;
import org.leoric.expensetracker.exchangerate.config.MarketDataProperties;
import org.leoric.expensetracker.exchangerate.models.ExchangeRateCache;
import org.leoric.expensetracker.exchangerate.repositories.ExchangeRateCacheRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceImplTest {

	@Mock
	private ExchangeRateCacheRepository cacheRepository;
	@Mock
	private FrankfurterClient frankfurterClient;
	@Mock
	private CoinGeckoClient coinGeckoClient;
	@Mock
	private MarketDataProperties properties;

	private ExchangeRateServiceImpl exchangeRateService;

	@BeforeEach
	void setUp() {
		exchangeRateService = spy(new ExchangeRateServiceImpl(cacheRepository, frankfurterClient, coinGeckoClient, properties));
	}

	@Test
	void convertAmount_shouldReturnNullWhenRoundedValueOverflowsPositiveRange() {
		Asset from = asset("CZK", 2);
		Asset to = asset("ETH", 18);
		LocalDate date = LocalDate.of(2026, 5, 31);

		doReturn(new BigDecimal("0.000020983276")).when(exchangeRateService).getRate(from, to, date);

		Long result = exchangeRateService.convertAmount(664381111L, from, to, date);

		assertThat(result).isNull();
	}

	@Test
	void convertAmount_shouldReturnNullWhenRoundedValueOverflowsNegativeRange() {
		Asset from = asset("CZK", 2);
		Asset to = asset("ETH", 18);
		LocalDate date = LocalDate.of(2026, 5, 31);

		doReturn(new BigDecimal("0.000020983276")).when(exchangeRateService).getRate(from, to, date);

		Long result = exchangeRateService.convertAmount(-664381111L, from, to, date);

		assertThat(result).isNull();
	}

	@Test
	void convertAmount_shouldReturnRoundedConvertedValueWhenWithinLongRange() {
		Asset from = asset("CZK", 2);
		Asset to = asset("ETH", 18);
		LocalDate date = LocalDate.of(2026, 5, 31);

		doReturn(new BigDecimal("0.000020983276")).when(exchangeRateService).getRate(from, to, date);

		Long result = exchangeRateService.convertAmount(10000L, from, to, date);

		assertThat(result).isEqualTo(2098327600000000L);
	}

	@Test
	void historicalRateReplacesLiveQuoteCachedOnThatDate() {
		LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
		Asset btc = Asset.builder().code("BTC").scale(8).assetType(AssetType.CRYPTO)
				.marketDataSource(MarketDataSource.COINGECKO).marketDataKey("bitcoin").build();
		Asset czk = Asset.builder().code("CZK").scale(2).assetType(AssetType.FIAT)
				.marketDataSource(MarketDataSource.FRANKFURTER).marketDataKey("czk").build();
		ExchangeRateCache cached = ExchangeRateCache.builder()
				.rate(new BigDecimal("2000000"))
				.fetchedAt(yesterday.atTime(12, 0).toInstant(ZoneOffset.UTC))
				.build();
		when(cacheRepository.findByBaseAssetCodeAndQuoteAssetCodeAndRateDate("BTC", "CZK", yesterday))
				.thenReturn(Optional.of(cached));
		when(coinGeckoClient.getHistoricalPrice("bitcoin", "czk", yesterday))
				.thenReturn(new BigDecimal("1900000"));

		assertThat(exchangeRateService.getRate(btc, czk, yesterday)).isEqualByComparingTo("1900000");
		verify(coinGeckoClient).getHistoricalPrice("bitcoin", "czk", yesterday);
	}

	@Test
	void historicalQuoteFetchedAfterItsDateIsReused() {
		LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
		Asset btc = Asset.builder().code("BTC").scale(8).assetType(AssetType.CRYPTO).build();
		Asset czk = Asset.builder().code("CZK").scale(2).assetType(AssetType.FIAT).build();
		ExchangeRateCache cached = ExchangeRateCache.builder()
				.rate(new BigDecimal("1900000"))
				.fetchedAt(Instant.now())
				.build();
		when(cacheRepository.findByBaseAssetCodeAndQuoteAssetCodeAndRateDate("BTC", "CZK", yesterday))
				.thenReturn(Optional.of(cached));

		assertThat(exchangeRateService.getRate(btc, czk, yesterday)).isEqualByComparingTo("1900000");
		verify(coinGeckoClient, never()).getHistoricalPrice("bitcoin", "czk", yesterday);
	}

	private Asset asset(String code, int scale) {
		return Asset.builder()
				.code(code)
				.name(code)
				.assetType(AssetType.FIAT)
				.marketDataSource(MarketDataSource.NONE)
				.scale(scale)
				.build();
	}
}