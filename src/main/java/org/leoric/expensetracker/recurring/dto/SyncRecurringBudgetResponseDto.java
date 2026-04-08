package org.leoric.expensetracker.recurring.dto;

public record SyncRecurringBudgetResponseDto(
		int templatesProcessed,
		int budgetPlansCreated
) {
}