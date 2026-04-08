package org.leoric.expensetracker.budget.dto;

import java.util.List;

public record BulkBudgetImportResponseDto(
		int totalReceived,
		int successCount,
		int failureCount,
		List<BulkBudgetImportSuccessDto> created,
		List<BulkBudgetImportFailureDto> failures
) {

	public record BulkBudgetImportSuccessDto(
			String budgetPlanName,
			String period,
			long amount,
			String currency,
			String categoryName,
			boolean categoryMatched,
			boolean recurringCreated
	) {
	}

	public record BulkBudgetImportFailureDto(
			String budgetPlanName,
			String period,
			long amount,
			String currency,
			String reason
	) {
	}
}