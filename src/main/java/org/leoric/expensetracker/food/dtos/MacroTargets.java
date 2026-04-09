package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;

public record MacroTargets(
		BigDecimal proteinG,
		BigDecimal fatG,
		BigDecimal carbsG
) {
}