package org.leoric.expensetracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssetExchangeRateQuoteResponseDto(
		UUID sourceHoldingId,
		String sourceAssetCode,
		UUID targetHoldingId,
		String targetAssetCode,
		LocalDate rateDate,
		BigDecimal exchangeRate
) {
}