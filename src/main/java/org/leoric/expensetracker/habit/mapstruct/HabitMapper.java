package org.leoric.expensetracker.habit.mapstruct;

import org.leoric.expensetracker.habit.dtos.HabitCompletionResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitScheduleSlotResponseDto;
import org.leoric.expensetracker.habit.models.Habit;
import org.leoric.expensetracker.habit.models.HabitCompletion;
import org.leoric.expensetracker.habit.models.HabitScheduleSlot;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface HabitMapper {

	default HabitResponseDto toResponseDto(Habit habit, List<HabitScheduleSlot> scheduleSlots) {
		return new HabitResponseDto(
				habit.getId(),
				habit.getName(),
				habit.getDescription(),
				habit.getHabitType(),
				habit.getExpectedMinutes(),
				habit.getValidFrom(),
				habit.getValidTo(),
				habit.isActive(),
				habit.getSortOrder(),
				habit.getSatisfactionScore(),
				habit.getUtilityScore(),
				habit.getEstimatedPrice(),
				toScheduleSlotResponseDtos(scheduleSlots),
				toOffsetDateTime(habit.getCreatedDate()),
				toOffsetDateTime(habit.getLastModifiedDate())
		);
	}

	default HabitScheduleSlotResponseDto toScheduleSlotResponseDto(HabitScheduleSlot slot) {
		return new HabitScheduleSlotResponseDto(
				slot.getId(),
				slot.getDayOfWeek(),
				slot.getDayBlock(),
				slot.getSortOrder()
		);
	}

	default List<HabitScheduleSlotResponseDto> toScheduleSlotResponseDtos(List<HabitScheduleSlot> slots) {
		return slots.stream()
				.map(this::toScheduleSlotResponseDto)
				.toList();
	}

	default HabitCompletionResponseDto toCompletionResponseDto(HabitCompletion completion) {
		return new HabitCompletionResponseDto(
				completion.getId(),
				completion.getHabit().getId(),
				completion.getDate(),
				completion.getStatus(),
				completion.getNote(),
				completion.getSatisfactionScore(),
				completion.getExecutionScore(),
				completion.getActualPrice(),
				toOffsetDateTime(completion.getCompletedAt()),
				toOffsetDateTime(completion.getCreatedDate()),
				toOffsetDateTime(completion.getLastModifiedDate())
		);
	}

	default OffsetDateTime toOffsetDateTime(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}