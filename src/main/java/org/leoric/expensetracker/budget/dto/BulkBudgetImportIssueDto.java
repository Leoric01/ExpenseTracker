package org.leoric.expensetracker.budget.dto;

public record BulkBudgetImportIssueDto(
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