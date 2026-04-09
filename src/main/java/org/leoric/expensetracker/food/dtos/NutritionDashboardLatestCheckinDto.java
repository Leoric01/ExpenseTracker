package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionDashboardLatestCheckinDto(
		Integer weekIndex,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		BigDecimal avgWeightKg,
		BigDecimal avgCaloriesKcal,
		BigDecimal bodyFatPercent,
		BigDecimal weightChangeFromStartKg,
		BigDecimal weightChangeFromPreviousCheckinKg,
		BigDecimal avgEstimatedTdeeKcal,
		Integer daysWithWeight,
		Integer daysWithCalories,
		Integer daysWithBodyMeasurements
) {
}