package org.leoric.expensetracker.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

public record BulkBudgetImportItemDto(
		@NotBlank(message = "Budget plan name is required")
		String budgetPlanName,

		@NotBlank(message = "Period is required")
		String period,

		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be positive")
		Long amount,

		@NotBlank(message = "Currency is required")
		@ValidCurrencyCode
		String currency,

		@NotBlank(message = "Category name is required")
		String categoryName,

		@Positive(message = "Interval value must be positive")
		Integer intervalValue,

		boolean recurring
) {
}