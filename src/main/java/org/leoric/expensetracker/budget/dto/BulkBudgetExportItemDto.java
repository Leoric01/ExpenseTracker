package org.leoric.expensetracker.budget.dto;

public record BulkBudgetExportItemDto(
		String budgetPlanName,
		String period,
		long amount,
		String currency,
		String categoryName,
		Integer intervalValue,
		boolean recurring
) {
}