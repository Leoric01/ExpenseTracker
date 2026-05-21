package org.leoric.expensetracker.kanban.dtos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KanbanCardResponseDto(
		UUID id,
		UUID stageId,
		UUID parentCardId,
		String title,
		String description,
		String imageUrl,
		Integer priority,
		LocalDate dueDate,
		Integer cardOrder,
		boolean active,
		List<KanbanTagResponseDto> tags,
		List<KanbanCardResponseDto> children,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}