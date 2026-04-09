package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.DailyNutritionLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyNutritionLogRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface DailyNutritionLogService {

	DailyNutritionLogResponseDto dailyNutritionLogUpsert(User currentUser, UUID trackerId, UpsertDailyNutritionLogRequestDto request);

	DailyNutritionLogResponseDto dailyNutritionLogFindByDate(User currentUser, UUID trackerId, LocalDate logDate);

	Page<DailyNutritionLogResponseDto> dailyNutritionLogFindAll(User currentUser, UUID trackerId, LocalDate from, LocalDate to, Pageable pageable);

	void dailyNutritionLogDeleteByDate(User currentUser, UUID trackerId, LocalDate logDate);
}