package org.leoric.expensetracker.todo.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.todo.dtos.TodoArchiveUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoStatusUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoUpsertRequestDto;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public interface TodoService {

	TodoResponseDto todoCreate(User currentUser, UUID trackerId, TodoUpsertRequestDto request);

	List<TodoResponseDto> todoFindTree(
			User currentUser,
			UUID trackerId,
			String search,
			Boolean active,
			TodoStatus status,
			TodoPriority priority,
			LocalDate dueFrom,
			LocalDate dueTo
	);

	TodoResponseDto todoFindById(User currentUser, UUID trackerId, UUID todoId);

	TodoResponseDto todoUpdate(User currentUser, UUID trackerId, UUID todoId, TodoUpsertRequestDto request);

	void todoUpdateStatus(User currentUser, UUID trackerId, UUID todoId, TodoStatusUpdateRequestDto request);

	void todoArchive(User currentUser, UUID trackerId, UUID todoId, TodoArchiveUpdateRequestDto request);

	void todoReplaceTags(User currentUser, UUID trackerId, UUID todoId, List<UUID> tagIds);

	void todoDelete(User currentUser, UUID trackerId, UUID todoId);
}