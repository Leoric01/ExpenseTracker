package org.leoric.expensetracker.habit.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.habit.dtos.HabitDayOverviewDto;
import org.leoric.expensetracker.habit.dtos.HabitResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitUpsertRequestDto;
import org.leoric.expensetracker.habit.dtos.HabitWeekOverviewResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface HabitService {

	HabitResponseDto habitCreate(User currentUser, UUID trackerId, HabitUpsertRequestDto request);

	HabitResponseDto habitFindById(User currentUser, UUID trackerId, UUID habitId);

	Page<HabitResponseDto> habitFindAll(
			User currentUser,
			UUID trackerId,
			String search,
			Boolean active,
			Pageable pageable
	);

	HabitResponseDto habitUpdate(User currentUser, UUID trackerId, UUID habitId, HabitUpsertRequestDto request);

	void habitDeactivate(User currentUser, UUID trackerId, UUID habitId);

	void habitActivate(User currentUser, UUID trackerId, UUID habitId);

	void habitDelete(User currentUser, UUID trackerId, UUID habitId);

	HabitDayOverviewDto habitFindAgendaForDate(User currentUser, UUID trackerId, LocalDate date);

	HabitWeekOverviewResponseDto habitFindWeekOverview(User currentUser, UUID trackerId, LocalDate weekStart);
}