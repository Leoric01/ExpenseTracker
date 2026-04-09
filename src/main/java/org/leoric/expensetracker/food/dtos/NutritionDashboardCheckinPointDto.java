package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionDashboardCheckinPointDto(
		Integer weekIndex,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		BigDecimal avgWeightKg,
		BigDecimal avgCaloriesKcal,
		BigDecimal bodyFatPercent,
		BigDecimal avgEstimatedTdeeKcal
) {
}