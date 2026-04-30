package org.leoric.expensetracker.budget.dto;

import java.util.UUID;

public record BulkBudgetImportByCategoryIdIssueDto(
		String budgetPlanName,
		String period,
		long amount,
		String currency,
		UUID categoryId,
		Integer intervalValue,
		boolean recurring,
		String reason
) {
}