package org.leoric.expensetracker.recurring.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringBudgetResponseDto(
		UUID id,
		String name,
		long amount,
		String currencyCode,
		PeriodType periodType,
		int intervalValue,
		UUID categoryId,
		String categoryName,
		LocalDate startDate,
		LocalDate endDate,
		LocalDate nextRunDate,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}