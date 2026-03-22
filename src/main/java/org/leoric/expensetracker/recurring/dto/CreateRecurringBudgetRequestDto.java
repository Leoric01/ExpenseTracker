package org.leoric.expensetracker.recurring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRecurringBudgetRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be positive")
		Long amount,

		@NotBlank(message = "Currency code is required")
		@ValidCurrencyCode
		String currencyCode,

		@NotNull(message = "Period type is required")
		PeriodType periodType,

		@Positive(message = "Interval value must be positive")
		Integer intervalValue,

		@NotNull(message = "Start date is required")
		LocalDate startDate,

		LocalDate endDate,

		UUID categoryId
) {
}