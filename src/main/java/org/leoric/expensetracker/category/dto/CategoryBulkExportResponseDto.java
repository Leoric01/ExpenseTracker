package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.util.List;

public record CategoryBulkExportResponseDto(
		String name,
		CategoryKind categoryKind,
		Integer sortOrder,
		List<CategoryBulkExportResponseDto> children
) {
	public CategoryBulkExportResponseDto {
		children = children == null ? List.of() : children;
	}
}