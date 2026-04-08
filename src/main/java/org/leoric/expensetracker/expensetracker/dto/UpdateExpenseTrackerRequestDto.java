package org.leoric.expensetracker.expensetracker.dto;

import java.util.UUID;

public record UpdateExpenseTrackerRequestDto(
		String name,

		String description,

		UUID preferredDisplayAssetId
) {
}