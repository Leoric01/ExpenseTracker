package org.leoric.expensetracker.food.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.MacroCalculationCommand;
import org.leoric.expensetracker.food.dtos.MacroTargets;
import org.leoric.expensetracker.food.dtos.ManualNutritionTargetRequestDto;
import org.leoric.expensetracker.food.dtos.NutritionTargetResponseDto;
import org.leoric.expensetracker.food.dtos.TdeeCalculationCommand;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.models.NutritionTarget;
import org.leoric.expensetracker.food.repositories.NutritionTargetRepository;
import org.leoric.expensetracker.food.services.interfaces.MacroCalculationService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.leoric.expensetracker.food.services.interfaces.NutritionTargetService;
import org.leoric.expensetracker.food.services.interfaces.TdeeCalculationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import static org.leoric.expensetracker.food.utility.NutritionConstants.ALGORITHM_VERSION;
import static org.leoric.expensetracker.food.utility.NutritionConstants.DAYS_PER_WEEK;
import static org.leoric.expensetracker.food.utility.NutritionConstants.KCAL_PER_KG;

@Service
@Slf4j
@RequiredArgsConstructor
public class NutritionTargetServiceImpl implements NutritionTargetService {

	private static final String INITIAL_REASON_CODE = "INITIAL_CALCULATION";
	private static final String MANUAL_OVERRIDE_REASON_CODE = "MANUAL_OVERRIDE";

	private final NutritionTargetRepository nutritionTargetRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;
	private final TdeeCalculationService tdeeCalculationService;
	private final MacroCalculationService macroCalculationService;

	@Override
	@Transactional
	public NutritionTargetResponseDto nutritionTargetGenerateInitial(User currentUser, UUID trackerId, UUID goalPlanId) {
		NutritionProfile profile = nutritionDomainLookupService.getNutritionProfileOrThrow(trackerId);
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		BigDecimal bodyFatPercent = resolveBodyFatPercent(goalPlan);

		BigDecimal baselineTdeeKcal = tdeeCalculationService.calculateBaselineTdee(
				new TdeeCalculationCommand(
						goalPlan.getStartWeightKg(),
						bodyFatPercent,
						profile.getActivityMultiplier()
				)
		);

		BigDecimal calorieAdjustmentKcal = goalPlan.getTargetWeeklyWeightChangeKg()
				.multiply(KCAL_PER_KG)
				.divide(DAYS_PER_WEEK, 2, RoundingMode.HALF_UP);

		BigDecimal targetCaloriesKcal = baselineTdeeKcal
				.add(calorieAdjustmentKcal)
				.setScale(2, RoundingMode.HALF_UP);

		MacroTargets macroTargets = macroCalculationService.calculate(
				new MacroCalculationCommand(
						goalPlan.getStartWeightKg(),
						bodyFatPercent,
						targetCaloriesKcal,
						goalPlan.getProteinStrategy(),
						goalPlan.getFatStrategy(),
						goalPlan.getCarbStrategy()
				)
		);

		closePreviousCurrentTargetIfExists(goalPlan.getId(), goalPlan.getStartDate().minusDays(1));

		NutritionTarget target = NutritionTarget.builder()
				.expenseTracker(goalPlan.getExpenseTracker())
				.goalPlan(goalPlan)
				.effectiveFrom(goalPlan.getStartDate())
				.effectiveTo(null)
				.baselineTdeeKcal(baselineTdeeKcal)
				.calorieAdjustmentKcal(calorieAdjustmentKcal)
				.targetCaloriesKcal(targetCaloriesKcal)
				.targetProteinG(macroTargets.proteinG())
				.targetFatG(macroTargets.fatG())
				.targetCarbsG(macroTargets.carbsG())
				.algorithmVersion(ALGORITHM_VERSION)
				.reasonCode(INITIAL_REASON_CODE)
				.reasonDetail("Initial target generated from goal plan setup")
				.manualOverride(false)
				.build();

		target = nutritionTargetRepository.save(target);

		log.info("User {} generated initial nutrition target for goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), goalPlan.getExpenseTracker().getName());

		return toResponse(target);
	}

	@Override
	@Transactional(readOnly = true)
	public NutritionTargetResponseDto nutritionTargetFindCurrent(User currentUser, UUID trackerId) {
		NutritionTarget target = nutritionDomainLookupService.getCurrentNutritionTargetOrThrow(trackerId);
		return toResponse(target);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<NutritionTargetResponseDto> nutritionTargetFindAll(User currentUser, UUID trackerId, Pageable pageable) {
		return nutritionTargetRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(this::toResponse);
	}

	@Override
	@Transactional
	public NutritionTargetResponseDto nutritionTargetManualOverride(User currentUser, UUID trackerId, UUID goalPlanId, ManualNutritionTargetRequestDto request) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		closePreviousCurrentTargetIfExists(goalPlanId, request.effectiveFrom().minusDays(1));

		NutritionTarget target = NutritionTarget.builder()
				.expenseTracker(goalPlan.getExpenseTracker())
				.goalPlan(goalPlan)
				.effectiveFrom(request.effectiveFrom())
				.effectiveTo(null)
				.baselineTdeeKcal(request.baselineTdeeKcal())
				.calorieAdjustmentKcal(request.calorieAdjustmentKcal())
				.targetCaloriesKcal(request.targetCaloriesKcal())
				.targetProteinG(request.targetProteinG())
				.targetFatG(request.targetFatG())
				.targetCarbsG(request.targetCarbsG())
				.algorithmVersion(ALGORITHM_VERSION)
				.reasonCode(MANUAL_OVERRIDE_REASON_CODE)
				.reasonDetail(request.reasonDetail())
				.manualOverride(true)
				.build();

		target = nutritionTargetRepository.save(target);

		log.info("User {} manually overridden nutrition target for goal plan '{}' in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), goalPlan.getExpenseTracker().getName());

		return toResponse(target);
	}

	private BigDecimal resolveBodyFatPercent(GoalPlan goalPlan) {
		if (goalPlan.getStartBodyFatPercent() != null) {
			return goalPlan.getStartBodyFatPercent();
		}
		return BigDecimal.ZERO;
	}

	private void closePreviousCurrentTargetIfExists(UUID goalPlanId, LocalDate effectiveTo) {
		nutritionTargetRepository.findTopByGoalPlanIdOrderByEffectiveFromDesc(goalPlanId)
				.ifPresent(existing -> {
					existing.setEffectiveTo(effectiveTo);
					nutritionTargetRepository.save(existing);
				});
	}

	private NutritionTargetResponseDto toResponse(NutritionTarget target) {
		return new NutritionTargetResponseDto(
				target.getId(),
				target.getExpenseTracker().getId(),
				target.getGoalPlan().getId(),
				target.getEffectiveFrom(),
				target.getEffectiveTo(),
				target.getBaselineTdeeKcal(),
				target.getCalorieAdjustmentKcal(),
				target.getTargetCaloriesKcal(),
				target.getTargetProteinG(),
				target.getTargetFatG(),
				target.getTargetCarbsG(),
				target.getAlgorithmVersion(),
				target.getReasonCode(),
				target.getReasonDetail(),
				target.isManualOverride(),
				target.getCreatedDate(),
				target.getLastModifiedDate()
		);
	}
}