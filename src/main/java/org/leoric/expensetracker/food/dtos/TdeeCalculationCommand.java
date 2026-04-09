package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;

public record TdeeCalculationCommand(
		BigDecimal weightKg,
		BigDecimal bodyFatPercent,
		BigDecimal activityMultiplier
) {
}