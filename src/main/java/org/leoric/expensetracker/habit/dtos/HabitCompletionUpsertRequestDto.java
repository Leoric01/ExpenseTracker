package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.CompletionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record HabitCompletionUpsertRequestDto(
		UUID habitId,
		LocalDate date,
		CompletionStatus status,
		String note,
		Integer satisfactionScore,
		Integer executionScore,
		Integer actualPrice
) {
}