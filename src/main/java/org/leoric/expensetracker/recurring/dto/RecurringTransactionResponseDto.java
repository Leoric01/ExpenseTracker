package org.leoric.expensetracker.recurring.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransactionResponseDto(
		UUID id,
		TransactionType transactionType,
		UUID holdingId,
		String holdingName,
		UUID categoryId,
		String categoryName,
		long amount,
		String assetCode,
		Integer assetScale,
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