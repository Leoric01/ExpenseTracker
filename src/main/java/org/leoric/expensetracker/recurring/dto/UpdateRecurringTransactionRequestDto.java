package org.leoric.expensetracker.recurring.dto;

import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateRecurringTransactionRequestDto(
		UUID holdingId,

		UUID categoryId,

		@Positive(message = "Amount must be positive")
		Long amount,

		@ValidCurrencyCode
		String currencyCode,

		String description,

		String note,

		PeriodType periodType,

		@Positive(message = "Interval value must be positive")
		Integer intervalValue,

		LocalDate startDate,

		LocalDate endDate
) {
}