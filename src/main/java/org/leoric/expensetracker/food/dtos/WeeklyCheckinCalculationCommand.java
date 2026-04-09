package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.DailyBodyMeasurementLog;
import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.leoric.expensetracker.food.models.WeeklyCheckin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyCheckinCalculationCommand(
		UUID expenseTrackerId,
		UUID goalPlanId,
		Integer weekIndex,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		BigDecimal startWeightKg,
		List<DailyNutritionLog> dailyNutritionLogs,
		List<DailyBodyMeasurementLog> dailyBodyMeasurementLogs,
		List<WeeklyCheckin> previousWeeklyCheckins
) {
}