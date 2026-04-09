package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.DailyCheckinResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyCheckinRequestDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface DailyCheckinService {

	DailyCheckinResponseDto dailyCheckinUpsert(User currentUser, UUID trackerId, UpsertDailyCheckinRequestDto request);
}