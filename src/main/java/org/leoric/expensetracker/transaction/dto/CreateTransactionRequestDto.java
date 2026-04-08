package org.leoric.expensetracker.transaction.dto;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequestDto(
		@NotNull(message = "Transaction type is required")
		TransactionType transactionType,

		UUID holdingId,

		UUID sourceHoldingId,

		UUID targetHoldingId,

		UUID categoryId,

		Long amount,

		Long correctedBalance,

		String currencyCode,

		BigDecimal exchangeRate,

		Long feeAmount,

		@NotNull(message = "Transaction date is required")
		Instant transactionDate,

		String description,

		String note,

		String externalRef
) {
}