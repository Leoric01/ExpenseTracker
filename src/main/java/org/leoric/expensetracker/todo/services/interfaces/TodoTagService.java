package org.leoric.expensetracker.todo.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoTagUpsertRequestDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface TodoTagService {

	TodoTagResponseDto todoTagCreate(User currentUser, UUID trackerId, TodoTagUpsertRequestDto request);

	List<TodoTagResponseDto> todoTagFindAll(User currentUser, UUID trackerId);

	TodoTagResponseDto todoTagUpdate(User currentUser, UUID trackerId, UUID tagId, TodoTagUpsertRequestDto request);

	void todoTagDelete(User currentUser, UUID trackerId, UUID tagId);
}