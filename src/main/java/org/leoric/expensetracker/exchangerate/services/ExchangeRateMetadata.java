package org.leoric.expensetracker.exchangerate.services;

import org.leoric.expensetracker.asset.models.constants.MarketDataSource;

import java.time.Instant;
import java.time.LocalDate;

public record ExchangeRateMetadata(
		MarketDataSource source,
		LocalDate rateDate,
		Instant fetchedAt,
		boolean dailyHistorical
) {
}
