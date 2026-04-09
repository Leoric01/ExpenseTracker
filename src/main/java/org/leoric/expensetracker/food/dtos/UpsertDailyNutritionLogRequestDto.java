package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertDailyNutritionLogRequestDto(
		LocalDate logDate,
		BigDecimal weightKg,
		BigDecimal caloriesKcal,
		BigDecimal proteinG,
		BigDecimal fatG,
		BigDecimal carbsG,
		String notes
) {
}