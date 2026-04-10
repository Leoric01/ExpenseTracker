package org.leoric.expensetracker.habit.repositories;

import org.leoric.expensetracker.habit.models.constants.CompletionStatus;
import org.leoric.expensetracker.habit.models.constants.DayBlock;
import org.leoric.expensetracker.habit.models.constants.HabitType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface HabitAgendaProjection {

	UUID getHabitId();

	String getHabitName();

	String getHabitDescription();

	HabitType getHabitType();

	Integer getExpectedMinutes();

	Integer getHabitSortOrder();

	Integer getSatisfactionScore();

	Integer getUtilityScore();

	Integer getEstimatedPrice();

	DayBlock getDayBlock();

	Integer getSlotSortOrder();

	UUID getCompletionId();

	LocalDate getCompletionDate();

	CompletionStatus getCompletionStatus();

	String getCompletionNote();

	Instant getCompletedAt();

	Integer getCompletionSatisfactionScore();

	Integer getCompletionExecutionScore();

	Integer getCompletionActualPrice();
}