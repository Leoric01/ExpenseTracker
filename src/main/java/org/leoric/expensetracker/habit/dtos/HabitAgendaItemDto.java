package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.DayBlock;
import org.leoric.expensetracker.habit.models.constants.HabitType;

import java.util.List;
import java.util.UUID;

public record HabitAgendaItemDto(
		UUID habitId,
		String name,
		String description,
		HabitType habitType,
		Integer expectedMinutes,
		Integer sortOrder,
		int satisfactionScore,
		int utilityScore,
		Integer estimatedPrice,
		List<DayBlock> dayBlocks,
		HabitCompletionStateDto completion
) {
}