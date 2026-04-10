package org.leoric.expensetracker.exchangerate.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Client for CoinGecko API (crypto currency prices).
 * <p>
 * API docs: <a href="https://docs.coingecko.com/v3.0.1/reference/introduction">CoinGecko</a>
 * <p>
 * Free tier: 10-30 calls/min. Uses coin ID (e.g. "bitcoin") and vs_currency (e.g. "czk").
 */
@Component
@Slf4j
public class CoinGeckoClient {

	private static final DateTimeFormatter COINGECKO_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final RestClient restClient;

	public CoinGeckoClient(@Qualifier("coinGeckoRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * Fetch the current price of a crypto asset in a target (fiat) currency.
	 *
	 * @param coinId         CoinGecko coin ID (e.g. "bitcoin", "ethereum")
	 * @param targetCurrency target currency code lowercase (e.g. "czk", "usd")
	 * @return the price (1 coin = rate target), or null if unavailable
	 */
	public BigDecimal getCurrentPrice(String coinId, String targetCurrency) {
		try {
			String vs = targetCurrency.toLowerCase();
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get()
					.uri("/simple/price?ids={coinId}&vs_currencies={vs}", coinId, vs)
					.retrieve()
					.body(Map.class);

			if (response == null || !response.containsKey(coinId)) {
				log.warn("CoinGecko returned no data for coin {}", coinId);
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> coinData = (Map<String, Object>) response.get(coinId);
			Object price = coinData.get(vs);
			if (price == null) {
				log.warn("CoinGecko returned no price for {}/{}", coinId, targetCurrency);
				return null;
			}

			return new BigDecimal(price.toString());
		} catch (RestClientException e) {
			log.error("CoinGecko API error for current price {}/{}: {}", coinId, targetCurrency, e.getMessage());
			return null;
		}
	}

	/**
	 * Fetch the historical price of a crypto asset on a specific date.
	 * <p>
	 * CoinGecko endpoint: /coins/{id}/history?date=dd-MM-yyyy
	 *
	 * @param coinId         CoinGecko coin ID (e.g. "bitcoin")
	 * @param targetCurrency target currency code lowercase (e.g. "czk")
	 * @param date           the date for which to fetch the price
	 * @return the price, or null if unavailable
	 */
	public BigDecimal getHistoricalPrice(String coinId, String targetCurrency, LocalDate date) {
		try {
			String dateStr = date.format(COINGECKO_DATE_FMT);
			String vs = targetCurrency.toLowerCase();

			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get()
					.uri("/coins/{coinId}/history?date={date}&localization=false", coinId, dateStr)
					.retrieve()
					.body(Map.class);

			if (response == null || !response.containsKey("market_data")) {
				log.warn("CoinGecko returned no market_data for {} on {}", coinId, date);
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> marketData = (Map<String, Object>) response.get("market_data");
			@SuppressWarnings("unchecked")
			Map<String, Object> currentPrice = (Map<String, Object>) marketData.get("current_price");

			if (currentPrice == null) {
				log.warn("CoinGecko returned no current_price for {} on {}", coinId, date);
				return null;
			}

			Object price = currentPrice.get(vs);
			if (price == null) {
				log.warn("CoinGecko returned no price for {}/{} on {}", coinId, targetCurrency, date);
				return null;
			}

			return new BigDecimal(price.toString());
		} catch (RestClientException e) {
			log.error("CoinGecko API error for historical price {}/{} on {}: {}", coinId, targetCurrency, date, e.getMessage());
			return null;
		}
	}
}