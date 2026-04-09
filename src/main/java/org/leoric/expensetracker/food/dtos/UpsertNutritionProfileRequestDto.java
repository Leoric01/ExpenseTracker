package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BiologicalSex;
import org.leoric.expensetracker.food.models.constants.UnitSystem;

import java.math.BigDecimal;

public record UpsertNutritionProfileRequestDto(
		UnitSystem preferredUnitSystem,
		BiologicalSex biologicalSex,
		BigDecimal heightCm,
		BigDecimal activityMultiplier,
		boolean bodyFatAutoCalculationEnabled
) {
}