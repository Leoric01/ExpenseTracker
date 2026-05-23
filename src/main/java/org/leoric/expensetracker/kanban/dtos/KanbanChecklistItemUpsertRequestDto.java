package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record KanbanChecklistItemUpsertRequestDto(
		@NotBlank(message = "Checklist item title is required")
		@Size(max = 180, message = "Checklist item title length must be <= 180")
		String title,
		@PositiveOrZero(message = "Checklist item order must be >= 0")
		Integer itemOrder
) {
}