package org.leoric.expensetracker.kanban.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record KanbanCardUpsertRequestDto(
		UUID stageId,
		UUID parentCardId,
		@NotBlank(message = "Card title is required")
		@Size(max = 180, message = "Card title length must be <= 180")
		String title,
		@Size(max = 1000, message = "Description length must be <= 1000")
		String description,
		@Pattern(
				regexp = "^(https?://res\\.cloudinary\\.com/.+)?$",
				message = "imageUrl must be a valid Cloudinary URL"
		)
		String imageUrl,
		@PositiveOrZero(message = "Priority must be >= 0")
		@Max(value = 10, message = "Priority must be <= 10")
		Integer priority,
		LocalDate dueDate,
		@PositiveOrZero(message = "Card order must be >= 0")
		Integer cardOrder,
		List<UUID> tagIds,
		Boolean active
) {
}