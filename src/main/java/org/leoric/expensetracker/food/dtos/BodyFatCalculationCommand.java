package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BiologicalSex;

import java.math.BigDecimal;

public record BodyFatCalculationCommand(
		BiologicalSex biologicalSex,
		BigDecimal heightCm,
		BigDecimal waistCm,
		BigDecimal neckCm,
		BigDecimal hipCm
) {
}