package org.leoric.expensetracker.kanban.mapstruct;

import org.leoric.expensetracker.kanban.dtos.KanbanBoardDetailResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface KanbanMapper {

	default KanbanBoardResponseDto toBoardResponse(KanbanBoard board) {
		return new KanbanBoardResponseDto(
				board.getId(),
				board.getName(),
				board.getDescription(),
				board.getBoardOrder(),
				board.isActive(),
				toOffsetDateTime(board.getCreatedDate()),
				toOffsetDateTime(board.getLastModifiedDate())
		);
	}

	default KanbanBoardDetailResponseDto toBoardDetailResponse(KanbanBoard board, List<KanbanStageResponseDto> stages) {
		return new KanbanBoardDetailResponseDto(
				board.getId(),
				board.getName(),
				board.getDescription(),
				board.getBoardOrder(),
				board.isActive(),
				stages,
				toOffsetDateTime(board.getCreatedDate()),
				toOffsetDateTime(board.getLastModifiedDate())
		);
	}

	default KanbanStageResponseDto toStageResponse(KanbanStage stage) {
		return new KanbanStageResponseDto(
				stage.getId(),
				stage.getName(),
				stage.getStageOrder(),
				stage.getColor(),
				stage.isVisibleOnBoard(),
				stage.isActive(),
				toOffsetDateTime(stage.getCreatedDate()),
				toOffsetDateTime(stage.getLastModifiedDate())
		);
	}

	default KanbanTagResponseDto toTagResponse(KanbanTag tag) {
		return new KanbanTagResponseDto(
				tag.getId(),
				tag.getName(),
				tag.getColor(),
				tag.getTagOrder(),
				tag.isActive(),
				toOffsetDateTime(tag.getCreatedDate()),
				toOffsetDateTime(tag.getLastModifiedDate())
		);
	}

	default KanbanCardResponseDto toCardResponse(KanbanCard card, List<KanbanTagResponseDto> tags) {
		return new KanbanCardResponseDto(
				card.getId(),
				card.getStage().getId(),
				card.getTitle(),
				card.getDescription(),
				card.getImageUrl(),
				card.getPriority(),
				card.getDueDate(),
				card.getCardOrder(),
				tags,
				toOffsetDateTime(card.getCreatedDate()),
				toOffsetDateTime(card.getLastModifiedDate())
		);
	}

	default OffsetDateTime toOffsetDateTime(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}