package org.leoric.expensetracker.transaction.dto;

import org.leoric.expensetracker.asset.models.constants.MarketDataSource;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionValuationResponseDto(
		String baseAssetCode,
		String quoteAssetCode,
		Instant transactionDate,
		BigDecimal rate,
		MarketDataSource source,
		Instant capturedAt,
		Instant fetchedAt,
		Boolean dailyHistorical
) {}
