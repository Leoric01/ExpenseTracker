package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.models.NutritionTarget;

import java.util.UUID;

public interface NutritionDomainLookupService {

	ExpenseTracker getTrackerOrThrow(UUID trackerId);

	NutritionProfile getNutritionProfileOrThrow(UUID trackerId);

	GoalPlan getGoalPlanOrThrow(UUID trackerId, UUID goalPlanId);

	GoalPlan getActiveGoalPlanOrThrow(UUID trackerId);

	NutritionTarget getCurrentNutritionTargetOrThrow(UUID trackerId);
}