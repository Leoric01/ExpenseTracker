package org.leoric.expensetracker.budget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkBudgetImportRequestDto(
		Integer totalItems,
		Integer itemCount,
		Integer issueCount,
		@NotNull(message = "Items are required")
		List<@Valid BulkBudgetImportItemDto> items,
		List<BulkBudgetImportIssueDto> issues
) {
}