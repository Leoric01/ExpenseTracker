package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.util.UUID;

public record UpdateCategoryRequestDto(
		String name,

		CategoryKind categoryKind,

		UUID parentId,

		Integer sortOrder
) {
}