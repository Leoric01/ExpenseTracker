package org.leoric.expensetracker.transaction.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateTransactionRequestDto(
		UUID holdingId,

		UUID sourceHoldingId,

		UUID targetHoldingId,

		@Positive(message = "Amount must be positive")
		Long amount,

		String currencyCode,

		@Positive(message = "Exchange rate must be positive")
		BigDecimal exchangeRate,

		Long feeAmount,

		Long settledAmount,

		UUID categoryId,

		Instant transactionDate,

		String description,

		String note,

		String externalRef
) {
}