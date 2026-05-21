package org.leoric.expensetracker.todo.mapstruct;

import org.leoric.expensetracker.todo.dtos.TodoResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.models.Todo;
import org.leoric.expensetracker.todo.models.TodoTag;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface TodoMapper {

	default TodoTagResponseDto toTagResponseDto(TodoTag tag) {
		return new TodoTagResponseDto(
				tag.getId(),
				tag.getName(),
				tag.getColor(),
				tag.isActive(),
				toOffsetDateTime(tag.getCreatedDate()),
				toOffsetDateTime(tag.getLastModifiedDate())
		);
	}

	default TodoResponseDto toResponseDto(Todo todo, List<TodoTagResponseDto> tags, List<TodoResponseDto> children) {
		return new TodoResponseDto(
				todo.getId(),
				todo.getParent() != null ? todo.getParent().getId() : null,
				todo.getTitle(),
				todo.getDescription(),
				todo.getStatus(),
				todo.getPriority(),
				todo.getDueDate(),
				toOffsetDateTime(todo.getCompletedAt()),
				toOffsetDateTime(todo.getArchivedAt()),
				todo.getEstimatedMinutes(),
				todo.getNote(),
				todo.getSortOrder(),
				todo.isActive(),
				tags,
				children,
				toOffsetDateTime(todo.getCreatedDate()),
				toOffsetDateTime(todo.getLastModifiedDate())
		);
	}

	default OffsetDateTime toOffsetDateTime(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}