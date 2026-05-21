package org.leoric.expensetracker.todo.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TodoTagResponseDto(
		UUID id,
		String name,
		String color,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}