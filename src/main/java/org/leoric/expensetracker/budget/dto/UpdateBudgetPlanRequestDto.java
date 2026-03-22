package org.leoric.expensetracker.budget.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateBudgetPlanRequestDto(
		String name,

		Long amount,

		@ValidCurrencyCode
		String currencyCode,

		PeriodType periodType,

		LocalDate validFrom,

		LocalDate validTo,

		UUID categoryId
) {
}