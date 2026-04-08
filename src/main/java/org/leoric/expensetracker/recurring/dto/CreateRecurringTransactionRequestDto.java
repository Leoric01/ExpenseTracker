package org.leoric.expensetracker.recurring.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.validation.ValidCurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRecurringTransactionRequestDto(
		@NotNull(message = "Transaction type is required")
		TransactionType transactionType,

		@NotNull(message = "Holding is required")
		UUID holdingId,

		UUID categoryId,

		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be positive")
		Long amount,

		@NotNull(message = "Currency code is required")
		@ValidCurrencyCode
		String currencyCode,

		String description,

		String note,

		@NotNull(message = "Period type is required")
		PeriodType periodType,

		@Positive(message = "Interval value must be positive")
		Integer intervalValue,

		@NotNull(message = "Start date is required")
		LocalDate startDate,

		LocalDate endDate
) {
}