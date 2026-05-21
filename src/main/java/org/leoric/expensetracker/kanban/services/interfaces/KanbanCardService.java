package org.leoric.expensetracker.kanban.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardSnapshotResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardMoveRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardUpsertRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public interface KanbanCardService {

	KanbanCardResponseDto kanbanCardCreate(User currentUser, UUID trackerId, UUID boardId, KanbanCardUpsertRequestDto request);

	KanbanBoardSnapshotResponseDto kanbanCardFindBoardSnapshot(User currentUser, UUID trackerId, UUID boardId, Boolean visibleOnly);

	KanbanCardResponseDto kanbanCardFindById(User currentUser, UUID trackerId, UUID boardId, UUID cardId);

	KanbanCardResponseDto kanbanCardUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardUpsertRequestDto request);

	KanbanCardResponseDto kanbanCardMove(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardMoveRequestDto request);

	KanbanCardResponseDto kanbanCardUploadImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId, MultipartFile file);

	void kanbanCardDeleteImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId);

	void kanbanCardDelete(User currentUser, UUID trackerId, UUID boardId, UUID cardId);
}