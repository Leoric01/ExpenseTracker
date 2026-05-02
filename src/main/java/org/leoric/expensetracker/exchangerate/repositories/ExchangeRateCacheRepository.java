package org.leoric.expensetracker.exchangerate.repositories;

import org.leoric.expensetracker.exchangerate.models.ExchangeRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateCacheRepository extends JpaRepository<ExchangeRateCache, UUID> {

	Optional<ExchangeRateCache> findByBaseAssetCodeAndQuoteAssetCodeAndRateDate(
			String baseAssetCode, String quoteAssetCode, LocalDate rateDate);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT INTO exchange_rate_cache (id, base_asset_code, quote_asset_code, rate_date, rate, source, fetched_at)
			VALUES (UNHEX(REPLACE(UUID(), '-', '')), :baseAssetCode, :quoteAssetCode, :rateDate, :rate, :source, :fetchedAt)
			ON DUPLICATE KEY UPDATE
				rate = VALUES(rate),
				source = VALUES(source),
				fetched_at = VALUES(fetched_at)
			""", nativeQuery = true)
	void upsertRate(
			@Param("baseAssetCode") String baseAssetCode,
			@Param("quoteAssetCode") String quoteAssetCode,
			@Param("rateDate") LocalDate rateDate,
			@Param("rate") BigDecimal rate,
			@Param("source") String source,
			@Param("fetchedAt") Instant fetchedAt
	);
}