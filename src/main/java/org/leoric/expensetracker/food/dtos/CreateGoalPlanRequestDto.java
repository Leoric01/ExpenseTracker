package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BodyFatSource;
import org.leoric.expensetracker.food.models.constants.CarbStrategy;
import org.leoric.expensetracker.food.models.constants.FatStrategy;
import org.leoric.expensetracker.food.models.constants.GoalType;
import org.leoric.expensetracker.food.models.constants.ProteinStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalPlanRequestDto(
		String name,
		GoalType goalType,
		LocalDate startDate,
		LocalDate endDate,
		BigDecimal startWeightKg,
		BigDecimal startBodyFatPercent,
		BodyFatSource startBodyFatSource,
		BigDecimal targetWeeklyWeightChangeKg,
		ProteinStrategy proteinStrategy,
		FatStrategy fatStrategy,
		CarbStrategy carbStrategy,
		String notes
) {
}