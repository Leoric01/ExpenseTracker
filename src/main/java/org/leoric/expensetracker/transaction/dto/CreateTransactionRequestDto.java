package org.leoric.expensetracker.transaction.dto;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequestDto(
		@NotNull(message = "Transaction type is required")
		TransactionType transactionType,

		UUID walletId,

		UUID sourceWalletId,

		UUID targetWalletId,

		UUID categoryId,

		Long amount,

		Long correctedBalance,

		@NotNull(message = "Transaction date is required")
		Instant transactionDate,

		String description,

		String note,

		String externalRef
) {
}