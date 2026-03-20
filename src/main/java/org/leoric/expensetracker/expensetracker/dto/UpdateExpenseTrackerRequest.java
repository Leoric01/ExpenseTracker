package org.leoric.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.Size;

public record UpdateExpenseTrackerRequest(
		String name,

		String description,

		@Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
		String defaultCurrencyCode
) {
}