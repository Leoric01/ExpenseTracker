package org.leoric.expensetracker.transaction.dto;

import java.util.UUID;

public record AssetExchangeRateQuoteRequestDto(
		UUID sourceHoldingId,
		UUID targetHoldingId
) {
}