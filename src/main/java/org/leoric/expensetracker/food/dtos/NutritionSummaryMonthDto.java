package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.YearMonth;

public record NutritionSummaryMonthDto(
		YearMonth month,
		BigDecimal actualWeightChangeKg,
		BigDecimal goalWeightChangeKg
) {
}