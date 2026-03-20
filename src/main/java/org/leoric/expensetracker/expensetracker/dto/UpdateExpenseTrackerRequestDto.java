package org.leoric.expensetracker.expensetracker.dto;

import org.leoric.expensetracker.validation.ValidCurrencyCode;

public record UpdateExpenseTrackerRequestDto(
		String name,

		String description,

		@ValidCurrencyCode
		String defaultCurrencyCode
) {
}