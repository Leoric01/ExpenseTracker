package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertDailyCheckinRequestDto(
		LocalDate logDate,
		BigDecimal weightKg,
		BigDecimal caloriesKcal,
		BigDecimal proteinG,
		BigDecimal fatG,
		BigDecimal carbsG,
		String notes,
		BigDecimal waistCm,
		BigDecimal neckCm,
		BigDecimal hipCm
) {
}