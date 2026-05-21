package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KanbanBoardUpsertRequestDto(
		@NotBlank(message = "Board name is required")
		@Size(max = 120, message = "Board name length must be <= 120")
		String name,
		@Size(max = 500, message = "Description length must be <= 500")
		String description,
		Boolean active
) {
}