package org.leoric.expensetracker.transaction.dto;

import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateTransactionRequestDto(
		UUID holdingId,

		@Positive(message = "Amount must be positive")
		Long amount,

		@ValidCurrencyCode
		String currencyCode,

		@Positive(message = "Exchange rate must be positive")
		BigDecimal exchangeRate,

		Long feeAmount,

		UUID categoryId,

		Instant transactionDate,

		String description,

		String note,

		String externalRef
) {
}