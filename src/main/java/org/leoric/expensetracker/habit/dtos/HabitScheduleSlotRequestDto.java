package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.DayBlock;

import java.time.DayOfWeek;

public record HabitScheduleSlotRequestDto(
		DayOfWeek dayOfWeek,
		DayBlock dayBlock,
		Integer sortOrder
) {
}