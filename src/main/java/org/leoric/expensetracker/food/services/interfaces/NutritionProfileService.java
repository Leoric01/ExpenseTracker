package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.NutritionProfileResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertNutritionProfileRequestDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface NutritionProfileService {

	NutritionProfileResponseDto nutritionProfileUpsert(User currentUser, UUID trackerId, UpsertNutritionProfileRequestDto request);

	NutritionProfileResponseDto nutritionProfileFind(User currentUser, UUID trackerId);
}