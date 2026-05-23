package org.leoric.expensetracker.kanban.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagUpsertRequestDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface KanbanTagService {

	KanbanTagResponseDto kanbanTagCreate(User currentUser, UUID trackerId, KanbanTagUpsertRequestDto request);

	List<KanbanTagResponseDto> kanbanTagCreateBulk(User currentUser, UUID trackerId, List<KanbanTagUpsertRequestDto> request);

	List<KanbanTagResponseDto> kanbanTagFindAll(User currentUser, UUID trackerId);

	KanbanTagResponseDto kanbanTagUpdate(User currentUser, UUID trackerId, UUID tagId, KanbanTagUpsertRequestDto request);

	void kanbanTagDelete(User currentUser, UUID trackerId, UUID tagId);
}