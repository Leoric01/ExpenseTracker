package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface WeeklyCheckinService {

	WeeklyCheckinResponseDto weeklyCheckinGenerate(User currentUser, UUID trackerId, UUID goalPlanId, Integer weekIndex);

	WeeklyCheckinResponseDto weeklyCheckinGenerateForDate(User currentUser, UUID trackerId, UUID goalPlanId, LocalDate date);

	WeeklyCheckinResponseDto weeklyCheckinFindByWeekIndex(User currentUser, UUID trackerId, UUID goalPlanId, Integer weekIndex);

	Page<WeeklyCheckinResponseDto> weeklyCheckinFindAll(User currentUser, UUID trackerId, UUID goalPlanId, Pageable pageable);
}