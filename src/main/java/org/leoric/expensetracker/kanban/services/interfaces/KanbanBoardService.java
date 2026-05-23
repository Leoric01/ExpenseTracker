package org.leoric.expensetracker.kanban.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardDetailResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageUpsertRequestDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface KanbanBoardService {

	KanbanBoardResponseDto kanbanBoardCreate(User currentUser, UUID trackerId, KanbanBoardUpsertRequestDto request);

	List<KanbanBoardResponseDto> kanbanBoardFindAll(User currentUser, UUID trackerId);

	KanbanBoardDetailResponseDto kanbanBoardFindById(User currentUser, UUID trackerId, UUID boardId);

	KanbanBoardResponseDto kanbanBoardUpdate(User currentUser, UUID trackerId, UUID boardId, KanbanBoardUpsertRequestDto request);

	void kanbanBoardReorder(User currentUser, UUID trackerId, KanbanBoardReorderRequestDto request);

	void kanbanBoardDelete(User currentUser, UUID trackerId, UUID boardId);

	KanbanStageResponseDto kanbanStageCreate(User currentUser, UUID trackerId, UUID boardId, KanbanStageUpsertRequestDto request);

	List<KanbanStageResponseDto> kanbanStageFindAll(User currentUser, UUID trackerId, UUID boardId);

	KanbanStageResponseDto kanbanStageUpdate(User currentUser, UUID trackerId, UUID boardId, UUID stageId, KanbanStageUpsertRequestDto request);

	void kanbanStageReorder(User currentUser, UUID trackerId, UUID boardId, KanbanStageReorderRequestDto request);

	void kanbanStageDelete(User currentUser, UUID trackerId, UUID boardId, UUID stageId);
}