package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.DayBlock;

import java.time.DayOfWeek;
import java.util.UUID;

public record HabitScheduleSlotResponseDto(
		UUID id,
		DayOfWeek dayOfWeek,
		DayBlock dayBlock,
		Integer sortOrder
) {
}