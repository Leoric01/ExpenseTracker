package org.leoric.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateExpenseTrackerRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		String description,

		UUID preferredDisplayAssetId
) {
}