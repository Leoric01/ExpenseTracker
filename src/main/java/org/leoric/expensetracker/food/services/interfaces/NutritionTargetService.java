package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.ManualNutritionTargetRequestDto;
import org.leoric.expensetracker.food.dtos.NutritionTargetResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface NutritionTargetService {

	NutritionTargetResponseDto nutritionTargetGenerateInitial(User currentUser, UUID trackerId, UUID goalPlanId);

	NutritionTargetResponseDto nutritionTargetFindCurrent(User currentUser, UUID trackerId);

	Page<NutritionTargetResponseDto> nutritionTargetFindAll(User currentUser, UUID trackerId, Pageable pageable);

	NutritionTargetResponseDto nutritionTargetManualOverride(User currentUser, UUID trackerId, UUID goalPlanId, ManualNutritionTargetRequestDto request);
}