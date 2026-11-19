package org.leoric.expensetracker.holding.dto;

public record HoldingPeriodPerformanceDto(
		Long convertedIncome,
		Long convertedExpense,
		Long result,
		boolean estimated,
		String unavailableReason
) {
}
