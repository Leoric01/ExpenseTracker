package org.leoric.expensetracker.kanban.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KanbanStageResponseDto(
		UUID id,
		String name,
		Integer stageOrder,
		boolean visibleOnBoard,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}