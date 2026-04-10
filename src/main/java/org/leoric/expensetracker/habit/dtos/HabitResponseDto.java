package org.leoric.expensetracker.habit.dtos;

import org.leoric.expensetracker.habit.models.constants.HabitType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HabitResponseDto(
		UUID id,
		String name,
		String description,
		HabitType habitType,
		Integer expectedMinutes,
		LocalDate validFrom,
		LocalDate validTo,
		boolean active,
		Integer sortOrder,
		int satisfactionScore,
		int utilityScore,
		Integer estimatedPrice,
		List<HabitScheduleSlotResponseDto> scheduleSlots,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}