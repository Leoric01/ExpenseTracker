package org.leoric.expensetracker.transaction.dto;

import java.time.Instant;
import java.util.UUID;

public record UpdateTransactionRequestDto(
		UUID categoryId,

		Instant transactionDate,

		String description,

		String note,

		String externalRef
) {
}