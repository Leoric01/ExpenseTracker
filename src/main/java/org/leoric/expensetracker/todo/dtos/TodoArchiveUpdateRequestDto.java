package org.leoric.expensetracker.todo.dtos;

import jakarta.validation.constraints.NotNull;

public record TodoArchiveUpdateRequestDto(
		@NotNull(message = "Archived flag is required")
		Boolean archived
) {
}