package org.leoric.expensetracker.exchangerate.services.interfaces;

import org.leoric.expensetracker.asset.models.Asset;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Service for fetching exchange rates between assets.
 * Supports both fiat (via Frankfurter) and crypto (via CoinGecko) with DB caching.
 */
public interface ExchangeRateService {

	/**
	 * Get the exchange rate to convert 1 unit of {@code fromAsset} into {@code toAsset} on the given date.
	 * <p>
	 * The returned rate means: 1 fromAsset = rate toAsset (in real/human units, not minor units).
	 * <p>
	 * Returns null if the rate cannot be determined (API error, unsupported pair, MANUAL/NONE source).
	 *
	 * @param fromAsset source asset
	 * @param toAsset   target (display) asset
	 * @param date      the date for the rate (use today for current)
	 * @return exchange rate, or null if unavailable
	 */
	BigDecimal getRate(Asset fromAsset, Asset toAsset, LocalDate date);

	default BigDecimal getRate(Asset fromAsset, Asset toAsset, Instant at) {
		return getRate(fromAsset, toAsset, at.atZone(ZoneOffset.UTC).toLocalDate());
	}

	/**
	 * Convert a minor-unit amount from one asset to another.
	 * <p>
	 * Handles scale differences between assets correctly.
	 * For example: 9971035 satoshi (BTC, scale=8) → CZK minor units (scale=2) using BTC/CZK rate.
	 *
	 * @param amountMinorUnits the amount in the source asset's minor units
	 * @param fromAsset        source asset
	 * @param toAsset          target asset
	 * @param date             the date for the exchange rate
	 * @return converted amount in the target asset's minor units, or null if rate unavailable
	 */
	Long convertAmount(long amountMinorUnits, Asset fromAsset, Asset toAsset, LocalDate date);

	default Long convertAmount(long amountMinorUnits, Asset fromAsset, Asset toAsset, Instant at) {
		return convertAmount(amountMinorUnits, fromAsset, toAsset, at.atZone(ZoneOffset.UTC).toLocalDate());
	}
}