package org.leoric.expensetracker.recurring.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransactionResponseDto(
		UUID id,
		TransactionType transactionType,
		UUID walletId,
		String walletName,
		UUID categoryId,
		String categoryName,
		long amount,
		String currencyCode,
		String description,
		String note,
		PeriodType periodType,
		int intervalValue,
		LocalDate startDate,
		LocalDate endDate,
		LocalDate nextRunDate,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}