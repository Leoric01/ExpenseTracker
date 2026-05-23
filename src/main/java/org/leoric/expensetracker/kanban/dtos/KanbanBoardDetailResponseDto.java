package org.leoric.expensetracker.kanban.dtos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KanbanBoardDetailResponseDto(
		UUID id,
		String name,
		String description,
		Integer boardOrder,
		boolean active,
		List<KanbanStageResponseDto> stages,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}