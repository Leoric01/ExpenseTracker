package org.leoric.expensetracker.transaction.dto;

import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record TransactionFilter(
		String search,
		UUID categoryId,
		UUID holdingId,
		TransactionType transactionType,
		TransactionStatus status,
		Instant dateFrom,
		Instant dateTo
) {
}