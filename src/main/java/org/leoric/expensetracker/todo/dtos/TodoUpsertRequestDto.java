package org.leoric.expensetracker.todo.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.image.services.ImageUrlRules;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TodoUpsertRequestDto(
		UUID parentTodoId,
		@NotBlank(message = "Title is required")
		@Size(max = 180, message = "Title length must be <= 180")
		String title,
		@Size(max = 1000, message = "Description length must be <= 1000")
		String description,
		@Pattern(
				regexp = ImageUrlRules.PATTERN,
				message = "imageUrl must be a valid stored image URL"
		)
		String imageUrl,
		TodoStatus status,
		TodoPriority priority,
		LocalDate dueDate,
		@PositiveOrZero(message = "Estimated minutes must be >= 0")
		Integer estimatedMinutes,
		@Size(max = 1000, message = "Note length must be <= 1000")
		String note,
		@PositiveOrZero(message = "Sort order must be >= 0")
		Integer sortOrder,
		Boolean active,
		List<UUID> tagIds
) {
}