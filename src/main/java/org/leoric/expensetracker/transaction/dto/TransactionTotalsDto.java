package org.leoric.expensetracker.transaction.dto;

public record TransactionTotalsDto(
		long incomeAmount,
		long expenseAmount,
		long netAmount
) {
}