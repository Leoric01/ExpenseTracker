package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record KanbanCardMoveRequestDto(
		UUID stageId,
		UUID parentCardId,
		@PositiveOrZero(message = "Card order must be >= 0")
		Integer cardOrder
) {
}