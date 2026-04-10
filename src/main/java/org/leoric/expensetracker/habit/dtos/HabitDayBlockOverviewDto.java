package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.DayBlock;

import java.util.List;

public record HabitDayBlockOverviewDto(
		DayBlock dayBlock,
		List<HabitAgendaItemDto> items
) {
}