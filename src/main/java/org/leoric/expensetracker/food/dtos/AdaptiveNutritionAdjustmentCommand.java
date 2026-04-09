package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionTarget;
import org.leoric.expensetracker.food.models.WeeklyCheckin;

import java.util.List;

public record AdaptiveNutritionAdjustmentCommand(
		GoalPlan goalPlan,
		NutritionTarget currentNutritionTarget,
		List<WeeklyCheckin> weeklyCheckins
) {
}