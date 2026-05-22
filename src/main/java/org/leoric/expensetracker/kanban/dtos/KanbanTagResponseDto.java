package org.leoric.expensetracker.kanban.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KanbanTagResponseDto(
		UUID id,
		String name,
		String color,
		Integer tagOrder,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}