package org.leoric.expensetracker.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAssetExchangeV2RequestDto(
		UUID sourceHoldingId,
		UUID targetHoldingId,
		Long amount,
		Long settledAmount,
		Long feeAmount,
		String currencyCode,
		BigDecimal exchangeRate,
		Instant transactionDate,
		String description,
		String note,
		String externalRef
) {
}