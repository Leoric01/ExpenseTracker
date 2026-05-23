package org.leoric.expensetracker.kanban.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KanbanChecklistItemResponseDto(
		UUID id,
		String title,
		boolean completed,
		OffsetDateTime completedDate,
		Integer itemOrder,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}