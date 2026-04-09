package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionDashboardCaloriePointDto(
		LocalDate date,
		BigDecimal caloriesKcal
) {
}