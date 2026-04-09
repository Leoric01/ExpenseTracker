package org.leoric.expensetracker.food.dtos;

public record DailyCheckinResponseDto(
		DailyNutritionLogResponseDto nutritionLog,
		DailyBodyMeasurementLogResponseDto bodyMeasurementLog
) {
}