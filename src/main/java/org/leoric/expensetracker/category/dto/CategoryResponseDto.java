package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CategoryResponseDto(
		UUID id,
		String name,
		CategoryKind categoryKind,
		UUID parentId,
		String parentName,
		Integer sortOrder,
		boolean active,
		String iconUrl,
		String iconColor,
		List<CategoryResponseDto> children,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}