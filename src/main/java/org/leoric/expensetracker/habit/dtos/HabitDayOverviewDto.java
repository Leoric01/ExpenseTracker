package org.leoric.expensetracker.habit.dtos;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record HabitDayOverviewDto(
		LocalDate date,
		DayOfWeek dayOfWeek,
		List<HabitDayBlockOverviewDto> blocks
) {
}