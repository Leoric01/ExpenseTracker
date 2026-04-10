package org.leoric.expensetracker.exchangerate.repositories;

import org.leoric.expensetracker.exchangerate.models.ExchangeRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateCacheRepository extends JpaRepository<ExchangeRateCache, UUID> {

	Optional<ExchangeRateCache> findByBaseAssetCodeAndQuoteAssetCodeAndRateDate(
			String baseAssetCode, String quoteAssetCode, LocalDate rateDate);
}