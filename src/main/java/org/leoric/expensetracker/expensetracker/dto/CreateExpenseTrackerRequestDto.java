package org.leoric.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

public record CreateExpenseTrackerRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		String description,

		@NotBlank(message = "Default currency code is required")
		@ValidCurrencyCode
		String defaultCurrencyCode
) {
}