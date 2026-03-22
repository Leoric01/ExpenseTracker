package org.leoric.expensetracker.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBudgetPlanRequestDto(
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

		@NotNull(message = "Valid from date is required")
		LocalDate validFrom,

		LocalDate validTo,

		UUID categoryId
) {
}