package org.leoric.expensetracker.budget.dto;

public record BulkBudgetExportIssueDto(
		String budgetPlanName,
		String period,
		long amount,
		String currency,
		String categoryName,
		Integer intervalValue,
		boolean recurring,
		String reason
) {
}