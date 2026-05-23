package org.leoric.expensetracker.kanban.dtos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KanbanCardResponseDto(
		UUID id,
		UUID stageId,
		String title,
		String description,
		String imageUrl,
		Integer priority,
		LocalDate dueDate,
		Integer cardOrder,
		List<KanbanTagResponseDto> tags,
		List<KanbanChecklistItemResponseDto> checklistItems,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}