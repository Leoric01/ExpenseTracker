package org.leoric.expensetracker.kanban.dtos;

import java.util.List;
import java.util.UUID;

public record KanbanStageCardsResponseDto(
		UUID id,
		String name,
		Integer stageOrder,
		boolean visibleOnBoard,
		List<KanbanCardResponseDto> cards
) {
}