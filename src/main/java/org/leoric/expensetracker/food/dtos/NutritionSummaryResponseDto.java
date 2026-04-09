package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.GoalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record NutritionSummaryResponseDto(
		UUID expenseTrackerId,
		UUID goalPlanId,
		String goalPlanName,
		GoalType goalType,
		LocalDate planStartDate,
		LocalDate planEndDate,
		BigDecimal startWeightKg,
		BigDecimal startBodyFatPercent,
		BigDecimal targetWeeklyWeightChangeKg,
		List<NutritionSummaryWeekDto> weeks,
		List<NutritionSummaryMonthDto> months
) {
}