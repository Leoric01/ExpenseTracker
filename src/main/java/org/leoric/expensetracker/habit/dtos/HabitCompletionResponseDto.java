package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.CompletionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HabitCompletionResponseDto(
		UUID id,
		UUID habitId,
		LocalDate date,
		CompletionStatus status,
		String note,
		int satisfactionScore,
		int executionScore,
		Integer actualPrice,
		OffsetDateTime completedAt,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}