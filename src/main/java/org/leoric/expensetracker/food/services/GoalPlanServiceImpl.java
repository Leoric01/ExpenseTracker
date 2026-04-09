package org.leoric.expensetracker.food.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.dtos.CreateGoalPlanRequestDto;
import org.leoric.expensetracker.food.dtos.GoalPlanResponseDto;
import org.leoric.expensetracker.food.dtos.NutritionTargetResponseDto;
import org.leoric.expensetracker.food.dtos.UpdateGoalPlanRequestDto;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.repositories.GoalPlanRepository;
import org.leoric.expensetracker.food.services.interfaces.GoalPlanService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.leoric.expensetracker.food.services.interfaces.NutritionTargetService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoalPlanServiceImpl implements GoalPlanService {

	private final GoalPlanRepository goalPlanRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;
	private final NutritionTargetService nutritionTargetService;

	@Override
	@Transactional
	public GoalPlanResponseDto goalPlanCreate(User currentUser, UUID trackerId, CreateGoalPlanRequestDto request) {
		ExpenseTracker tracker = nutritionDomainLookupService.getTrackerOrThrow(trackerId);
		NutritionProfile nutritionProfile = nutritionDomainLookupService.getNutritionProfileOrThrow(trackerId);

		if (goalPlanRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new OperationNotPermittedException(
					"Goal plan with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		deactivateExistingActiveGoalPlanIfNeeded(trackerId);

		GoalPlan goalPlan = GoalPlan.builder()
				.expenseTracker(tracker)
				.nutritionProfile(nutritionProfile)
				.name(request.name())
				.goalType(request.goalType())
				.startDate(request.startDate())
				.endDate(request.endDate())
				.startWeightKg(request.startWeightKg())
				.startBodyFatPercent(request.startBodyFatPercent())
				.startBodyFatSource(request.startBodyFatSource())
				.targetWeeklyWeightChangeKg(request.targetWeeklyWeightChangeKg())
				.proteinStrategy(request.proteinStrategy())
				.fatStrategy(request.fatStrategy())
				.carbStrategy(request.carbStrategy())
				.active(true)
				.notes(request.notes())
				.build();

		goalPlan = goalPlanRepository.save(goalPlan);

		log.info("User {} created goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), tracker.getName());

		NutritionTargetResponseDto initialTarget = nutritionTargetService
				.nutritionTargetGenerateInitial(currentUser, trackerId, goalPlan.getId());

		return toResponse(goalPlan, initialTarget);
	}

	@Override
	@Transactional(readOnly = true)
	public GoalPlanResponseDto goalPlanFindById(User currentUser, UUID trackerId, UUID goalPlanId) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);
		return toResponse(goalPlan);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<GoalPlanResponseDto> goalPlanFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		Page<GoalPlan> page = (search == null || search.isBlank())
				? goalPlanRepository.findByExpenseTrackerId(trackerId, pageable)
				: goalPlanRepository.findByExpenseTrackerIdWithSearch(trackerId, search.trim(), pageable);

		return page.map(this::toResponse);
	}

	@Override
	@Transactional
	public GoalPlanResponseDto goalPlanUpdate(User currentUser, UUID trackerId, UUID goalPlanId, UpdateGoalPlanRequestDto request) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		if (request.name() != null && !request.name().equalsIgnoreCase(goalPlan.getName())
				&& goalPlanRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new OperationNotPermittedException(
					"Goal plan with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		if (request.name() != null) {
			goalPlan.setName(request.name());
		}
		if (request.goalType() != null) {
			goalPlan.setGoalType(request.goalType());
		}
		if (request.startDate() != null) {
			goalPlan.setStartDate(request.startDate());
		}
		if (request.endDate() != null) {
			goalPlan.setEndDate(request.endDate());
		}
		if (request.startWeightKg() != null) {
			goalPlan.setStartWeightKg(request.startWeightKg());
		}
		if (request.startBodyFatPercent() != null) {
			goalPlan.setStartBodyFatPercent(request.startBodyFatPercent());
		}
		if (request.startBodyFatSource() != null) {
			goalPlan.setStartBodyFatSource(request.startBodyFatSource());
		}
		if (request.targetWeeklyWeightChangeKg() != null) {
			goalPlan.setTargetWeeklyWeightChangeKg(request.targetWeeklyWeightChangeKg());
		}
		if (request.proteinStrategy() != null) {
			goalPlan.setProteinStrategy(request.proteinStrategy());
		}
		if (request.fatStrategy() != null) {
			goalPlan.setFatStrategy(request.fatStrategy());
		}
		if (request.carbStrategy() != null) {
			goalPlan.setCarbStrategy(request.carbStrategy());
		}
		if (request.notes() != null) {
			goalPlan.setNotes(request.notes());
		}
		if (request.active() != null) {
			if (Boolean.TRUE.equals(request.active())) {
				deactivateExistingActiveGoalPlanIfNeeded(trackerId);
				goalPlan.setActive(true);
			} else {
				goalPlan.setActive(false);
			}
		}

		goalPlan = goalPlanRepository.save(goalPlan);

		log.info("User {} updated goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), goalPlan.getExpenseTracker().getName());

		return toResponse(goalPlan);
	}

	@Override
	@Transactional
	public GoalPlanResponseDto goalPlanActivate(User currentUser, UUID trackerId, UUID goalPlanId) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		deactivateExistingActiveGoalPlanIfNeeded(trackerId);
		goalPlan.setActive(true);
		goalPlan = goalPlanRepository.save(goalPlan);

		log.info("User {} activated goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), goalPlan.getExpenseTracker().getName());

		return toResponse(goalPlan);
	}

	@Override
	@Transactional
	public void goalPlanDeactivate(User currentUser, UUID trackerId, UUID goalPlanId) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);
		goalPlan.setActive(false);
		goalPlanRepository.save(goalPlan);

		log.info("User {} deactivated goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), goalPlan.getExpenseTracker().getName());
	}

	private void deactivateExistingActiveGoalPlanIfNeeded(UUID trackerId) {
		goalPlanRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)
				.ifPresent(existing -> {
					existing.setActive(false);
					goalPlanRepository.save(existing);
				});
	}

	private GoalPlanResponseDto toResponse(GoalPlan goalPlan) {
		return toResponse(goalPlan, null);
	}

	private GoalPlanResponseDto toResponse(GoalPlan goalPlan, NutritionTargetResponseDto initialNutritionTarget) {
		return new GoalPlanResponseDto(
				goalPlan.getId(),
				goalPlan.getExpenseTracker().getId(),
				goalPlan.getNutritionProfile().getId(),
				goalPlan.getName(),
				goalPlan.getGoalType(),
				goalPlan.getStartDate(),
				goalPlan.getEndDate(),
				goalPlan.getStartWeightKg(),
				goalPlan.getStartBodyFatPercent(),
				goalPlan.getStartBodyFatSource(),
				goalPlan.getTargetWeeklyWeightChangeKg(),
				goalPlan.getProteinStrategy(),
				goalPlan.getFatStrategy(),
				goalPlan.getCarbStrategy(),
				goalPlan.isActive(),
				goalPlan.getNotes(),
				goalPlan.getCreatedDate(),
				goalPlan.getLastModifiedDate(),
				initialNutritionTarget
		);
	}
}