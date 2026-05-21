package org.leoric.expensetracker.todo.dtos;

import java.util.List;
import java.util.UUID;

public record TodoTagReplaceRequestDto(
		List<UUID> tagIds
) {
}