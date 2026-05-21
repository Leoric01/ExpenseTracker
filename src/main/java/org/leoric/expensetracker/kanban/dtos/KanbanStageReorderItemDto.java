package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record KanbanStageReorderItemDto(
		@NotNull(message = "Stage id is required")
		UUID stageId,
		@PositiveOrZero(message = "Stage order must be >= 0")
		Integer stageOrder
) {
}