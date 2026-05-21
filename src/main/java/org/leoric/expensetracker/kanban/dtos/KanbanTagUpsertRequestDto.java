package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record KanbanTagUpsertRequestDto(
		@NotBlank(message = "Tag name is required")
		@Size(max = 80, message = "Tag name length must be <= 80")
		String name,
		@Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be in #RRGGBB format")
		String color,
		Boolean active
) {
}