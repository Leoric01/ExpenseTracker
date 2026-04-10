package org.leoric.expensetracker.exchangerate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.market-data")
public class MarketDataProperties {

	private Frankfurter frankfurter = new Frankfurter();
	private CoinGecko coingecko = new CoinGecko();

	/**
	 * How many hours a cached rate stays valid before re-fetching.
	 * Today's rates are always refreshed if older than this.
	 * Historical rates (past dates) are cached indefinitely.
	 */
	private int cacheHours = 6;

	@Getter
	@Setter
	public static class Frankfurter {
		private String baseUrl = "https://api.frankfurter.dev/v1";
	}

	@Getter
	@Setter
	public static class CoinGecko {
		private String baseUrl = "https://api.coingecko.com/api/v3";
		private String apiKey;
	}
}