package org.leoric.expensetracker.kanban.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KanbanBoardResponseDto(
		UUID id,
		String name,
		String description,
		Integer boardOrder,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}