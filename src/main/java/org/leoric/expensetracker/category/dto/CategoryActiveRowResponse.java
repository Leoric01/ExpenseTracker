package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.util.List;

public record CategoryActiveRowResponse(
		String id,
		String name,
		CategoryKind categoryKind,
		String parentId,
		int sortOrder,
		List<CategoryActiveBudgetPlanEmbed> budgetPlansForPeriod
) {
}