package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;

public record AdaptiveNutritionAdjustmentResult(
		BigDecimal previousTargetCaloriesKcal,
		BigDecimal newTargetCaloriesKcal,
		BigDecimal observedWeightChangeKg,
		BigDecimal expectedWeightChangeKg,
		BigDecimal estimatedCalorieErrorKcal,
		BigDecimal appliedAdjustmentKcal,
		BigDecimal baselineTdeeKcal,
		BigDecimal calorieAdjustmentKcal,
		BigDecimal targetCaloriesKcal,
		BigDecimal targetProteinG,
		BigDecimal targetFatG,
		BigDecimal targetCarbsG,
		String algorithmVersion,
		String reasonCode,
		String reasonDetail
) {
}