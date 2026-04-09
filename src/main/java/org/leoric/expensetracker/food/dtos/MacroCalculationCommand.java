package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.CarbStrategy;
import org.leoric.expensetracker.food.models.constants.FatStrategy;
import org.leoric.expensetracker.food.models.constants.ProteinStrategy;

import java.math.BigDecimal;

public record MacroCalculationCommand(
		BigDecimal weightKg,
		BigDecimal bodyFatPercent,
		BigDecimal targetCaloriesKcal,
		ProteinStrategy proteinStrategy,
		FatStrategy fatStrategy,
		CarbStrategy carbStrategy
) {
}