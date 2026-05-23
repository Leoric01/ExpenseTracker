package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record KanbanBoardReorderItemDto(
		@NotNull(message = "Board id is required")
		UUID boardId,
		@PositiveOrZero(message = "Board order must be >= 0")
		Integer boardOrder
) {
}