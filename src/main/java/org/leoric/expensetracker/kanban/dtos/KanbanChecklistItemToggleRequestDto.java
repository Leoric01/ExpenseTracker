package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.NotNull;

public record KanbanChecklistItemToggleRequestDto(
		@NotNull(message = "completed is required")
		Boolean completed
) {
}