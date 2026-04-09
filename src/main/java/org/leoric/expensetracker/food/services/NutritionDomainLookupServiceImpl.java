package org.leoric.expensetracker.food.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.models.NutritionTarget;
import org.leoric.expensetracker.food.repositories.GoalPlanRepository;
import org.leoric.expensetracker.food.repositories.NutritionProfileRepository;
import org.leoric.expensetracker.food.repositories.NutritionTargetRepository;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NutritionDomainLookupServiceImpl implements NutritionDomainLookupService {

	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final NutritionProfileRepository nutritionProfileRepository;
	private final GoalPlanRepository goalPlanRepository;
	private final NutritionTargetRepository nutritionTargetRepository;

	@Override
	@Transactional(readOnly = true)
	public ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found with id: " + trackerId));
	}

	@Override
	@Transactional(readOnly = true)
	public NutritionProfile getNutritionProfileOrThrow(UUID trackerId) {
		return nutritionProfileRepository.findByExpenseTrackerId(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Nutrition profile not found for expense tracker id: " + trackerId));
	}

	@Override
	@Transactional(readOnly = true)
	public GoalPlan getGoalPlanOrThrow(UUID trackerId, UUID goalPlanId) {
		return goalPlanRepository.findByIdAndExpenseTrackerId(goalPlanId, trackerId)
				.orElseThrow(() -> new EntityNotFoundException(
						"Goal plan not found with id: %s in expense tracker id: %s".formatted(goalPlanId, trackerId)));
	}

	@Override
	@Transactional(readOnly = true)
	public GoalPlan getActiveGoalPlanOrThrow(UUID trackerId) {
		return goalPlanRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Active goal plan not found for expense tracker id: " + trackerId));
	}

	@Override
	@Transactional(readOnly = true)
	public NutritionTarget getCurrentNutritionTargetOrThrow(UUID trackerId) {
		return nutritionTargetRepository.findTopByExpenseTrackerIdOrderByEffectiveFromDesc(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Current nutrition target not found for expense tracker id: " + trackerId));
	}
}