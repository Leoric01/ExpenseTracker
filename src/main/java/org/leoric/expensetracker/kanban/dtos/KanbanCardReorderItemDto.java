package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record KanbanCardReorderItemDto(
		@NotNull(message = "Card id is required")
		UUID cardId,
		@PositiveOrZero(message = "Card order must be >= 0")
		Integer cardOrder
) {
}