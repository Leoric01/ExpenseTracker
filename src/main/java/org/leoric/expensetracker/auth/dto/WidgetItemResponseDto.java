package org.leoric.expensetracker.auth.dto;

import java.util.UUID;

public record WidgetItemResponseDto(
		UUID entityId,
		int sortOrder
) {
}