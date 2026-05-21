package org.leoric.expensetracker.kanban.dtos;

import java.util.List;
import java.util.UUID;

public record KanbanBoardSnapshotResponseDto(
		UUID boardId,
		String boardName,
		List<KanbanStageCardsResponseDto> stages
) {
}