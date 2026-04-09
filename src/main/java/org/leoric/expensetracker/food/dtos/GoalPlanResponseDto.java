package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BodyFatSource;
import org.leoric.expensetracker.food.models.constants.CarbStrategy;
import org.leoric.expensetracker.food.models.constants.FatStrategy;
import org.leoric.expensetracker.food.models.constants.GoalType;
import org.leoric.expensetracker.food.models.constants.ProteinStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalPlanResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UUID nutritionProfileId,
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
		boolean active,
		String notes,
		Instant createdDate,
		Instant lastModifiedDate,
		NutritionTargetResponseDto initialNutritionTarget
) {
}