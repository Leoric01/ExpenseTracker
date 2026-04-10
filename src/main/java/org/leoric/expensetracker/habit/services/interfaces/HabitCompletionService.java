package org.leoric.expensetracker.habit.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.habit.dtos.HabitCompletionResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitCompletionUpsertRequestDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface HabitCompletionService {

	HabitCompletionResponseDto habitCompletionUpsert(
			User currentUser,
			UUID trackerId,
			HabitCompletionUpsertRequestDto request
	);
}