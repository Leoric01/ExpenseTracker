package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record KanbanChecklistItemReorderRequestDto(
		@NotEmpty(message = "Reorder items are required")
		List<@Valid KanbanChecklistItemReorderItemDto> items
) {
}