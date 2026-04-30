package org.leoric.expensetracker.budget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkBudgetImportByCategoryIdRequestDto(
		int totalItems,
		int itemCount,
		int issueCount,
		@NotNull(message = "Items are required")
		List<@Valid BulkBudgetImportByCategoryIdItemDto> items,
		List<BulkBudgetImportByCategoryIdIssueDto> issues
) {
}