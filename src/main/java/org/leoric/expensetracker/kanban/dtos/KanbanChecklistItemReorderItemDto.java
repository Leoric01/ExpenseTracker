package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record KanbanChecklistItemReorderItemDto(
		@NotNull(message = "Checklist item id is required")
		UUID itemId,
		@PositiveOrZero(message = "Checklist item order must be >= 0")
		Integer itemOrder
) {
}