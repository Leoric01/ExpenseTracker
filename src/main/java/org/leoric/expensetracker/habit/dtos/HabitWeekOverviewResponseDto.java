package org.leoric.expensetracker.habit.dtos;

import java.time.LocalDate;
import java.util.List;

public record HabitWeekOverviewResponseDto(
		LocalDate weekStart,
		LocalDate weekEnd,
		List<HabitDayOverviewDto> days
) {
}