package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.NutritionDashboardResponseDto;
import org.leoric.expensetracker.food.dtos.NutritionSummaryResponseDto;

import java.time.LocalDate;
import java.util.UUID;

public interface NutritionDashboardService {

	NutritionDashboardResponseDto nutritionDashboard(User currentUser, UUID trackerId, LocalDate from, LocalDate to);

	NutritionSummaryResponseDto nutritionSummary(User currentUser, UUID trackerId, UUID goalPlanId);
}