package org.leoric.expensetracker.todo.dtos;

import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TodoResponseDto(
		UUID id,
		UUID parentTodoId,
		String title,
		String description,
		String imageUrl,
		TodoStatus status,
		TodoPriority priority,
		LocalDate dueDate,
		OffsetDateTime completedAt,
		OffsetDateTime archivedAt,
		Integer estimatedMinutes,
		String note,
		Integer sortOrder,
		boolean active,
		List<TodoTagResponseDto> tags,
		List<TodoResponseDto> children,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}