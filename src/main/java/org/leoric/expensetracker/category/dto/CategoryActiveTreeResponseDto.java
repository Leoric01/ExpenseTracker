package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.util.List;
import java.util.UUID;

public record CategoryActiveTreeResponseDto(
		UUID id,
		String name,
		CategoryKind categoryKind,
		UUID parentId,
		String parentName,
		Integer sortOrder,
		UUID budgetPlanId,
		String budgetPlanName,
		String assetCode,
		List<CategoryActiveTreeResponseDto> children
) {
}