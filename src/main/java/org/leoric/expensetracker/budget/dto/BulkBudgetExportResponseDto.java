package org.leoric.expensetracker.budget.dto;

import java.util.List;

public record BulkBudgetExportResponseDto(
		int totalItems,
		int itemCount,
		int issueCount,
		List<BulkBudgetExportItemDto> items,
		List<BulkBudgetExportIssueDto> issues
) {
}