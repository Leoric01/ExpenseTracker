package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualNutritionTargetRequestDto(
		LocalDate effectiveFrom,
		BigDecimal baselineTdeeKcal,
		BigDecimal calorieAdjustmentKcal,
		BigDecimal targetCaloriesKcal,
		BigDecimal targetProteinG,
		BigDecimal targetFatG,
		BigDecimal targetCarbsG,
		String reasonDetail
) {
}