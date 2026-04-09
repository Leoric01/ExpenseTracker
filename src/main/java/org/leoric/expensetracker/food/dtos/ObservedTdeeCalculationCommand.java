package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;

public record ObservedTdeeCalculationCommand(
		BigDecimal averageDailyCaloriesKcal,
		BigDecimal observedWeeklyWeightChangeKg
) {
}