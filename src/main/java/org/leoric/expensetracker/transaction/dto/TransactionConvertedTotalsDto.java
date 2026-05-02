package org.leoric.expensetracker.transaction.dto;

public record TransactionConvertedTotalsDto(
		Long incomeAmount,
		Long expenseAmount,
		Long netAmount,
		String convertedInto
) {
}