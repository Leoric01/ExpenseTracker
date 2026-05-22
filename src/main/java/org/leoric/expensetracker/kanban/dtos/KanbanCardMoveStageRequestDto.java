package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record KanbanCardMoveStageRequestDto(
		@NotNull(message = "targetStageId is required")
		UUID targetStageId
) {
}