package org.leoric.expensetracker.exchangerate.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Client for Frankfurter API (fiat currency exchange rates).
 * <p>
 * API docs: <a href="https://api.frankfurter.dev/v1">Frankfurter</a>
 * <p>
 * Supports any fiat-to-fiat conversion with historical date support.
 * Example: GET /2024-01-15?base=USD&symbols=CZK
 */
@Component
@Slf4j
public class FrankfurterClient {

	private final RestClient restClient;

	public FrankfurterClient(@Qualifier("frankfurterRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * Fetch exchange rate for a specific date.
	 *
	 * @param base   base currency code (e.g. "USD")
	 * @param target target currency code (e.g. "CZK")
	 * @param date   the date for which to fetch the rate
	 * @return the exchange rate (1 base = rate target), or null if unavailable
	 */
	public BigDecimal getRate(String base, String target, LocalDate date) {
		if (base.equalsIgnoreCase(target)) {
			return BigDecimal.ONE;
		}

		try {
			String dateStr = date.toString(); // yyyy-MM-dd
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get()
					.uri("/{date}?base={base}&symbols={target}", dateStr, base, target)
					.retrieve()
					.body(Map.class);

			if (response == null || !response.containsKey("rates")) {
				log.warn("Frankfurter returned no rates for {}/{} on {}", base, target, date);
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> rates = (Map<String, Object>) response.get("rates");
			Object rateValue = rates.get(target.toUpperCase());
			if (rateValue == null) {
				log.warn("Frankfurter returned no rate for target {} on {}", target, date);
				return null;
			}

			return new BigDecimal(rateValue.toString());
		} catch (RestClientException e) {
			log.error("Frankfurter API error for {}/{} on {}: {}", base, target, date, e.getMessage());
			return null;
		}
	}

	/**
	 * Fetch the latest exchange rate (today).
	 */
	public BigDecimal getLatestRate(String base, String target) {
		if (base.equalsIgnoreCase(target)) {
			return BigDecimal.ONE;
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get()
					.uri("/latest?base={base}&symbols={target}", base, target)
					.retrieve()
					.body(Map.class);

			if (response == null || !response.containsKey("rates")) {
				log.warn("Frankfurter returned no latest rates for {}/{}", base, target);
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> rates = (Map<String, Object>) response.get("rates");
			Object rateValue = rates.get(target.toUpperCase());
			if (rateValue == null) {
				log.warn("Frankfurter returned no latest rate for target {}", target);
				return null;
			}

			return new BigDecimal(rateValue.toString());
		} catch (RestClientException e) {
			log.error("Frankfurter API error for latest {}/{}: {}", base, target, e.getMessage());
			return null;
		}
	}
}