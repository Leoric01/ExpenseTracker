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
import org.leoric.expensetracker.exchangerate.repositories.ExchangeRateCacheRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
	void convertAmount_shouldClipToLongMaxWhenRoundedValueOverflows() {
		Asset from = asset("CZK", 2);
		Asset to = asset("ETH", 18);
		LocalDate date = LocalDate.of(2026, 5, 31);

		doReturn(new BigDecimal("0.000020983276")).when(exchangeRateService).getRate(from, to, date);

		Long result = exchangeRateService.convertAmount(664381111L, from, to, date);

		assertThat(result).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	void convertAmount_shouldClipToLongMinWhenRoundedValueOverflowsNegativeRange() {
		Asset from = asset("CZK", 2);
		Asset to = asset("ETH", 18);
		LocalDate date = LocalDate.of(2026, 5, 31);

		doReturn(new BigDecimal("0.000020983276")).when(exchangeRateService).getRate(from, to, date);

		Long result = exchangeRateService.convertAmount(-664381111L, from, to, date);

		assertThat(result).isEqualTo(Long.MIN_VALUE);
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