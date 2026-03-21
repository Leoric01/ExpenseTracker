package org.leoric.expensetracker.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.category.models.constants.CategoryKind;

import java.util.UUID;

public record CreateCategoryRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Category kind is required")
		CategoryKind categoryKind,

		UUID parentId,

		Integer sortOrder
) {
}