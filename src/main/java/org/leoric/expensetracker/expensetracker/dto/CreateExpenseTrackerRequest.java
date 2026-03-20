package org.leoric.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExpenseTrackerRequest(
		@NotBlank(message = "Name is required")
		String name,

		String description,

		@NotBlank(message = "Default currency code is required")
		@Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
		String defaultCurrencyCode
) {
}