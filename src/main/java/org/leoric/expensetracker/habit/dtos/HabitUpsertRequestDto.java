package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.HabitType;

import java.time.LocalDate;
import java.util.List;

public record HabitUpsertRequestDto(
		String name,
		String description,
		HabitType habitType,
		Integer expectedMinutes,
		LocalDate validFrom,
		LocalDate validTo,
		boolean active,
		Integer sortOrder,
		Integer satisfactionScore,
		Integer utilityScore,
		Integer estimatedPrice,
		List<HabitScheduleSlotRequestDto> scheduleSlots
) {
}