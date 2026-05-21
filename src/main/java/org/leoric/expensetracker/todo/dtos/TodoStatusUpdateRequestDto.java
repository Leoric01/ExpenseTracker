package org.leoric.expensetracker.todo.dtos;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;

public record TodoStatusUpdateRequestDto(
		@NotNull(message = "Status is required")
		TodoStatus status
) {
}