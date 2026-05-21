package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record KanbanStageUpsertRequestDto(
		@NotBlank(message = "Stage name is required")
		@Size(max = 80, message = "Stage name length must be <= 80")
		String name,
		@PositiveOrZero(message = "Stage order must be >= 0")
		Integer stageOrder,
		Boolean visibleOnBoard,
		Boolean active
) {
}