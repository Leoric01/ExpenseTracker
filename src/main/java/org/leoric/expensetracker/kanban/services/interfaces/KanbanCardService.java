package org.leoric.expensetracker.kanban.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardSnapshotResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemToggleRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemUpsertRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public interface KanbanCardService {
	KanbanCardResponseDto kanbanCardMove(User currentUser, UUID trackerId, UUID cardId, UUID targetStageId);
	KanbanCardResponseDto kanbanCardCreate(User currentUser, UUID trackerId, UUID boardId, KanbanCardUpsertRequestDto request);

	KanbanBoardSnapshotResponseDto kanbanCardFindBoardSnapshot(User currentUser, UUID trackerId, UUID boardId, Boolean visibleOnly);

	KanbanCardResponseDto kanbanCardFindById(User currentUser, UUID trackerId, UUID boardId, UUID cardId);

	KanbanCardResponseDto kanbanCardUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardUpsertRequestDto request);

	KanbanCardResponseDto kanbanChecklistItemCreate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanChecklistItemUpsertRequestDto request);

	KanbanCardResponseDto kanbanChecklistItemUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId, KanbanChecklistItemUpsertRequestDto request);

	KanbanCardResponseDto kanbanChecklistItemToggle(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId, KanbanChecklistItemToggleRequestDto request);

	KanbanCardResponseDto kanbanChecklistItemReorder(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanChecklistItemReorderRequestDto request);

	void kanbanChecklistItemDelete(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId);


	KanbanCardResponseDto kanbanCardUploadImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId, MultipartFile file);

	void kanbanCardDeleteImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId);

	void kanbanCardDelete(User currentUser, UUID trackerId, UUID boardId, UUID cardId);
}