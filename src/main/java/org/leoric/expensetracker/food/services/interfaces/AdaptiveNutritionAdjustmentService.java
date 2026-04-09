package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentCommand;
import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentResult;
import org.springframework.stereotype.Service;

@Service
public interface AdaptiveNutritionAdjustmentService {

	AdaptiveNutritionAdjustmentResult adjust(AdaptiveNutritionAdjustmentCommand command);
}