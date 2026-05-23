package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record KanbanBoardReorderRequestDto(
		@NotEmpty(message = "Reorder items are required")
		List<@Valid KanbanBoardReorderItemDto> items
) {
}