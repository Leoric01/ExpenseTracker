package org.leoric.expensetracker.budget.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BudgetPlanResponseDto(
		UUID id,
		String name,
		long amount,
		String assetCode,
		Integer assetScale,
		PeriodType periodType,
		UUID categoryId,
		String categoryName,
		LocalDate validFrom,
		LocalDate validTo,
		boolean active,
		long alreadySpent,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}