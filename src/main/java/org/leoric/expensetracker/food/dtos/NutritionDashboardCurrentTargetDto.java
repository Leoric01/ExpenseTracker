package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionDashboardCurrentTargetDto(
		BigDecimal baselineTdeeKcal,
		BigDecimal calorieAdjustmentKcal,
		BigDecimal targetCaloriesKcal,
		BigDecimal targetProteinG,
		BigDecimal targetFatG,
		BigDecimal targetCarbsG,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		boolean manualOverride
) {
}