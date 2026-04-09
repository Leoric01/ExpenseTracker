package org.leoric.expensetracker.food.dtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record NutritionDashboardResponseDto(
		UUID expenseTrackerId,
		UUID activeGoalPlanId,
		LocalDate from,
		LocalDate to,
		NutritionDashboardCurrentTargetDto currentTarget,
		NutritionDashboardLatestCheckinDto latestCheckin,
		List<NutritionDashboardWeightPointDto> weightTimeline,
		List<NutritionDashboardCaloriePointDto> calorieTimeline,
		List<NutritionDashboardCheckinPointDto> weeklyCheckins
) {
}