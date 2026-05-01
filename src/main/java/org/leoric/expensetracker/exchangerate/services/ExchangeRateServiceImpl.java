package org.leoric.expensetracker.exchangerate.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.exchangerate.clients.CoinGeckoClient;
import org.leoric.expensetracker.exchangerate.clients.FrankfurterClient;
import org.leoric.expensetracker.exchangerate.config.MarketDataProperties;
import org.leoric.expensetracker.exchangerate.models.ExchangeRateCache;
import org.leoric.expensetracker.exchangerate.repositories.ExchangeRateCacheRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

	private final ExchangeRateCacheRepository cacheRepository;
	private final FrankfurterClient frankfurterClient;
	private final CoinGeckoClient coinGeckoClient;
	private final MarketDataProperties properties;

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public BigDecimal getRate(Asset fromAsset, Asset toAsset, LocalDate date) {
		if (fromAsset.getCode().equalsIgnoreCase(toAsset.getCode())) {
			return BigDecimal.ONE;
		}

		// Check DB cache first
		Optional<ExchangeRateCache> cached = cacheRepository
				.findByBaseAssetCodeAndQuoteAssetCodeAndRateDate(fromAsset.getCode(), toAsset.getCode(), date);

		if (cached.isPresent() && isCacheValid(cached.get(), date)) {
			log.debug("Cache hit for {}/{} on {}", fromAsset.getCode(), toAsset.getCode(), date);
			return cached.get().getRate();
		}

		// Fetch from external API
		BigDecimal rate = fetchRate(fromAsset, toAsset, date);
		if (rate != null) {
			saveToCache(fromAsset.getCode(), toAsset.getCode(), date, rate, resolveSource(fromAsset, toAsset));
		}

		return rate;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Long convertAmount(long amountMinorUnits, Asset fromAsset, Asset toAsset, LocalDate date) {
		if (fromAsset.getCode().equalsIgnoreCase(toAsset.getCode())) {
			return amountMinorUnits;
		}

		BigDecimal rate = getRate(fromAsset, toAsset, date);
		if (rate == null) {
			return null;
		}

		// Convert: targetMinorUnits = sourceMinorUnits * rate * 10^(targetScale - sourceScale)
		int scaleDiff = toAsset.getScale() - fromAsset.getScale();
		BigDecimal scaleFactor = BigDecimal.TEN.pow(Math.abs(scaleDiff));

		BigDecimal result;
		if (scaleDiff >= 0) {
			result = BigDecimal.valueOf(amountMinorUnits)
					.multiply(rate)
					.multiply(scaleFactor);
		} else {
			result = BigDecimal.valueOf(amountMinorUnits)
					.multiply(rate)
					.divide(scaleFactor, 0, RoundingMode.HALF_UP);
		}

		return result.setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	/**
	 * Determine the best strategy to fetch the rate based on asset types.
	 */
	private BigDecimal fetchRate(Asset fromAsset, Asset toAsset, LocalDate date) {
		boolean isToday = !date.isBefore(LocalDate.now(ZoneOffset.UTC));

		// Case 1: Both FIAT → Frankfurter
		if (fromAsset.getAssetType() == AssetType.FIAT && toAsset.getAssetType() == AssetType.FIAT) {
			return fetchFiatToFiat(fromAsset, toAsset, date, isToday);
		}

		// Case 2: CRYPTO → FIAT → CoinGecko directly
		if (fromAsset.getAssetType() == AssetType.CRYPTO && toAsset.getAssetType() == AssetType.FIAT) {
			return fetchCryptoToFiat(fromAsset, toAsset, date, isToday);
		}

		// Case 3: FIAT → CRYPTO → invert CRYPTO→FIAT
		if (fromAsset.getAssetType() == AssetType.FIAT && toAsset.getAssetType() == AssetType.CRYPTO) {
			BigDecimal cryptoToFiat = fetchCryptoToFiat(toAsset, fromAsset, date, isToday);
			if (cryptoToFiat != null && cryptoToFiat.compareTo(BigDecimal.ZERO) != 0) {
				return BigDecimal.ONE.divide(cryptoToFiat, 12, RoundingMode.HALF_UP);
			}
			return null;
		}

		// Case 4: CRYPTO → CRYPTO → go through USD as intermediary
		if (fromAsset.getAssetType() == AssetType.CRYPTO && toAsset.getAssetType() == AssetType.CRYPTO) {
			return fetchCryptoToCrypto(fromAsset, toAsset, date, isToday);
		}

		log.warn("Unsupported conversion: {} ({}) → {} ({})",
				fromAsset.getCode(), fromAsset.getAssetType(), toAsset.getCode(), toAsset.getAssetType());
		return null;
	}

	private BigDecimal fetchFiatToFiat(Asset from, Asset to, LocalDate date, boolean isToday) {
		String baseKey = from.getMarketDataKey();
		String targetKey = to.getMarketDataKey();

		if (baseKey == null || targetKey == null) {
			log.warn("Missing marketDataKey for fiat conversion {}/{}", from.getCode(), to.getCode());
			return null;
		}

		return isToday
				? frankfurterClient.getLatestRate(baseKey, targetKey)
				: frankfurterClient.getRate(baseKey, targetKey, date);
	}

	private BigDecimal fetchCryptoToFiat(Asset crypto, Asset fiat, LocalDate date, boolean isToday) {
		String coinId = crypto.getMarketDataKey();
		String fiatCode = fiat.getMarketDataKey();

		if (coinId == null || fiatCode == null) {
			log.warn("Missing marketDataKey for crypto/fiat conversion {}/{}", crypto.getCode(), fiat.getCode());
			return null;
		}

		return isToday
				? coinGeckoClient.getCurrentPrice(coinId, fiatCode)
				: coinGeckoClient.getHistoricalPrice(coinId, fiatCode, date);
	}

	private BigDecimal fetchCryptoToCrypto(Asset from, Asset to, LocalDate date, boolean isToday) {
		// Use USD as intermediary: fromCrypto→USD / toCrypto→USD
		BigDecimal fromToUsd = fetchCryptoToFiatByCode(from, date, isToday);
		BigDecimal toToUsd = fetchCryptoToFiatByCode(to, date, isToday);

		if (fromToUsd != null && toToUsd != null && toToUsd.compareTo(BigDecimal.ZERO) != 0) {
			return fromToUsd.divide(toToUsd, 12, RoundingMode.HALF_UP);
		}
		return null;
	}

	private BigDecimal fetchCryptoToFiatByCode(Asset crypto, LocalDate date, boolean isToday) {
		String coinId = crypto.getMarketDataKey();
		if (coinId == null) return null;

		return isToday
				? coinGeckoClient.getCurrentPrice(coinId, "usd")
				: coinGeckoClient.getHistoricalPrice(coinId, "usd", date);
	}

	private boolean isCacheValid(ExchangeRateCache cached, LocalDate date) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);

		// Historical rates never expire
		if (date.isBefore(today)) {
			return true;
		}

		// Today's rates expire after configured hours
		Instant expiresAt = cached.getFetchedAt()
				.plusSeconds((long) properties.getCacheHours() * 3600);
		return Instant.now().isBefore(expiresAt);
	}

	private void saveToCache(String baseCode, String quoteCode, LocalDate date, BigDecimal rate, MarketDataSource source) {
		try {
			Optional<ExchangeRateCache> existing = cacheRepository
					.findByBaseAssetCodeAndQuoteAssetCodeAndRateDate(baseCode, quoteCode, date);

			ExchangeRateCache entry;
			if (existing.isPresent()) {
				entry = existing.get();
				entry.setRate(rate);
				entry.setFetchedAt(Instant.now());
			} else {
				entry = ExchangeRateCache.builder()
						.baseAssetCode(baseCode)
						.quoteAssetCode(quoteCode)
						.rateDate(date)
						.rate(rate)
						.source(source)
						.fetchedAt(Instant.now())
						.build();
			}
			cacheRepository.save(entry);
			log.debug("Cached rate {}/{} on {} = {}", baseCode, quoteCode, date, rate);
		} catch (Exception e) {
			log.warn("Failed to cache exchange rate {}/{} on {}: {}", baseCode, quoteCode, date, e.getMessage());
		}
	}

	private MarketDataSource resolveSource(Asset from, Asset to) {
		if (from.getMarketDataSource() == MarketDataSource.COINGECKO
				|| to.getMarketDataSource() == MarketDataSource.COINGECKO) {
			return MarketDataSource.COINGECKO;
		}
		if (from.getMarketDataSource() == MarketDataSource.FRANKFURTER
				|| to.getMarketDataSource() == MarketDataSource.FRANKFURTER) {
			return MarketDataSource.FRANKFURTER;
		}
		return MarketDataSource.NONE;
	}
}