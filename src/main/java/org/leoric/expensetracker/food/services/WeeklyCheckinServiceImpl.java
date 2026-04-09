package org.leoric.expensetracker.food.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentCommand;
import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentResult;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinCalculationCommand;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinResponseDto;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinSnapshot;
import org.leoric.expensetracker.food.models.DailyBodyMeasurementLog;
import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionTarget;
import org.leoric.expensetracker.food.models.TdeeAdjustmentEvent;
import org.leoric.expensetracker.food.models.WeeklyCheckin;
import org.leoric.expensetracker.food.repositories.DailyBodyMeasurementLogRepository;
import org.leoric.expensetracker.food.repositories.DailyNutritionLogRepository;
import org.leoric.expensetracker.food.repositories.NutritionTargetRepository;
import org.leoric.expensetracker.food.repositories.TdeeAdjustmentEventRepository;
import org.leoric.expensetracker.food.repositories.WeeklyCheckinRepository;
import org.leoric.expensetracker.food.services.interfaces.AdaptiveNutritionAdjustmentService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.leoric.expensetracker.food.services.interfaces.WeeklyCheckinCalculationService;
import org.leoric.expensetracker.food.services.interfaces.WeeklyCheckinService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyCheckinServiceImpl implements WeeklyCheckinService {

	private final WeeklyCheckinRepository weeklyCheckinRepository;
	private final DailyNutritionLogRepository dailyNutritionLogRepository;
	private final DailyBodyMeasurementLogRepository dailyBodyMeasurementLogRepository;
	private final TdeeAdjustmentEventRepository tdeeAdjustmentEventRepository;
	private final NutritionTargetRepository nutritionTargetRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;
	private final WeeklyCheckinCalculationService weeklyCheckinCalculationService;
	private final AdaptiveNutritionAdjustmentService adaptiveNutritionAdjustmentService;

	@Override
	@Transactional
	public WeeklyCheckinResponseDto weeklyCheckinGenerate(User currentUser, UUID trackerId, UUID goalPlanId, Integer weekIndex) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		LocalDate weekStartDate = goalPlan.getStartDate().plusWeeks(weekIndex - 1L);
		LocalDate weekEndDate = weekStartDate.plusDays(6);

		List<DailyNutritionLog> nutritionLogs = dailyNutritionLogRepository
				.findByGoalPlanIdAndLogDateBetweenOrderByLogDateAsc(goalPlanId, weekStartDate, weekEndDate);

		List<DailyBodyMeasurementLog> bodyMeasurementLogs = dailyBodyMeasurementLogRepository
				.findByGoalPlanIdAndLogDateBetweenOrderByLogDateAsc(goalPlanId, weekStartDate, weekEndDate);

		List<WeeklyCheckin> previousWeeklyCheckins = weeklyCheckinRepository.findByGoalPlanIdOrderByWeekIndexAsc(goalPlanId)
				.stream()
				.filter(w -> w.getWeekIndex() < weekIndex)
				.toList();

		WeeklyCheckinSnapshot snapshot = weeklyCheckinCalculationService.calculate(
				new WeeklyCheckinCalculationCommand(
						trackerId,
						goalPlanId,
						weekIndex,
						weekStartDate,
						weekEndDate,
						goalPlan.getStartWeightKg(),
						nutritionLogs,
						bodyMeasurementLogs,
						previousWeeklyCheckins
				)
		);

		WeeklyCheckin weeklyCheckin = weeklyCheckinRepository.findByGoalPlanIdAndWeekIndex(goalPlanId, weekIndex)
				.orElseGet(() -> WeeklyCheckin.builder()
						.expenseTracker(goalPlan.getExpenseTracker())
						.goalPlan(goalPlan)
						.weekIndex(weekIndex)
						.build());

		weeklyCheckin.setWeekStartDate(snapshot.weekStartDate());
		weeklyCheckin.setWeekEndDate(snapshot.weekEndDate());
		weeklyCheckin.setAvgWeightKg(snapshot.avgWeightKg());
		weeklyCheckin.setAvgCaloriesKcal(snapshot.avgCaloriesKcal());
		weeklyCheckin.setBodyFatPercent(snapshot.bodyFatPercent());
		weeklyCheckin.setWeightChangeFromStartKg(snapshot.weightChangeFromStartKg());
		weeklyCheckin.setWeightChangeFromPreviousCheckinKg(snapshot.weightChangeFromPreviousCheckinKg());
		weeklyCheckin.setAvgEstimatedTdeeKcal(snapshot.avgEstimatedTdeeKcal());
		weeklyCheckin.setDaysWithWeight(snapshot.daysWithWeight());
		weeklyCheckin.setDaysWithCalories(snapshot.daysWithCalories());
		weeklyCheckin.setDaysWithBodyMeasurements(snapshot.daysWithBodyMeasurements());

		weeklyCheckin = weeklyCheckinRepository.save(weeklyCheckin);

		runAdaptiveAdjustmentIfEligible(goalPlan, weeklyCheckin);

		log.info("User {} generated weekly checkin for goal plan '{}' week {} in tracker '{}'",
		         currentUser.getEmail(), goalPlan.getName(), weekIndex, goalPlan.getExpenseTracker().getName());

		return toResponse(weeklyCheckin);
	}

	@Override
	@Transactional
	public WeeklyCheckinResponseDto weeklyCheckinGenerateForDate(User currentUser, UUID trackerId, UUID goalPlanId, LocalDate date) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);

		long daysBetween = ChronoUnit.DAYS.between(goalPlan.getStartDate(), date);
		if (daysBetween < 0) {
			throw new IllegalArgumentException(
					"Date %s is before goal plan start date %s".formatted(date, goalPlan.getStartDate()));
		}

		int weekIndex = (int) (daysBetween / 7) + 1;

		return weeklyCheckinGenerate(currentUser, trackerId, goalPlanId, weekIndex);
	}

	@Override
	@Transactional(readOnly = true)
	public WeeklyCheckinResponseDto weeklyCheckinFindByWeekIndex(User currentUser, UUID trackerId, UUID goalPlanId, Integer weekIndex) {
		WeeklyCheckin weeklyCheckin = weeklyCheckinRepository.findByGoalPlanIdAndWeekIndex(goalPlanId, weekIndex)
				.orElseThrow(() -> new EntityNotFoundException(
						"Weekly checkin not found for goal plan id %s and week index %s".formatted(goalPlanId, weekIndex)));

		if (!weeklyCheckin.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException(
					"Weekly checkin not found for goal plan id %s and week index %s".formatted(goalPlanId, weekIndex));
		}

		return toResponse(weeklyCheckin);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<WeeklyCheckinResponseDto> weeklyCheckinFindAll(User currentUser, UUID trackerId, UUID goalPlanId, Pageable pageable) {
		return weeklyCheckinRepository.findByGoalPlanIdAndExpenseTrackerId(goalPlanId, trackerId, pageable)
				.map(this::toResponse);
	}

	private void runAdaptiveAdjustmentIfEligible(GoalPlan goalPlan, WeeklyCheckin newlySavedCheckin) {
		if (tdeeAdjustmentEventRepository.findByWeeklyCheckinId(newlySavedCheckin.getId()).isPresent()) {
			return;
		}
		List<WeeklyCheckin> allCheckins = weeklyCheckinRepository.findByGoalPlanIdOrderByWeekIndexAsc(goalPlan.getId());

		if (allCheckins.size() < 4) {
			return;
		}

		NutritionTarget currentTarget = nutritionTargetRepository.findTopByGoalPlanIdOrderByEffectiveFromDesc(goalPlan.getId())
				.orElseThrow(() -> new EntityNotFoundException(
						"Current nutrition target not found for goal plan id: " + goalPlan.getId()));

		if (currentTarget.isManualOverride()) {
			return;
		}

		AdaptiveNutritionAdjustmentResult result = adaptiveNutritionAdjustmentService.adjust(
				new AdaptiveNutritionAdjustmentCommand(goalPlan, currentTarget, allCheckins)
		);

		currentTarget.setEffectiveTo(newlySavedCheckin.getWeekEndDate());
		nutritionTargetRepository.save(currentTarget);

		NutritionTarget newTarget = NutritionTarget.builder()
				.expenseTracker(goalPlan.getExpenseTracker())
				.goalPlan(goalPlan)
				.effectiveFrom(newlySavedCheckin.getWeekEndDate().plusDays(1))
				.effectiveTo(null)
				.baselineTdeeKcal(result.baselineTdeeKcal())
				.calorieAdjustmentKcal(result.calorieAdjustmentKcal())
				.targetCaloriesKcal(result.targetCaloriesKcal())
				.targetProteinG(result.targetProteinG())
				.targetFatG(result.targetFatG())
				.targetCarbsG(result.targetCarbsG())
				.algorithmVersion(result.algorithmVersion())
				.reasonCode(result.reasonCode())
				.reasonDetail(result.reasonDetail())
				.manualOverride(false)
				.build();

		nutritionTargetRepository.save(newTarget);

		TdeeAdjustmentEvent adjustmentEvent = TdeeAdjustmentEvent.builder()
				.expenseTracker(goalPlan.getExpenseTracker())
				.goalPlan(goalPlan)
				.weeklyCheckin(newlySavedCheckin)
				.previousTargetCaloriesKcal(result.previousTargetCaloriesKcal())
				.newTargetCaloriesKcal(result.newTargetCaloriesKcal())
				.observedWeightChangeKg(result.observedWeightChangeKg())
				.expectedWeightChangeKg(result.expectedWeightChangeKg())
				.estimatedCalorieErrorKcal(result.estimatedCalorieErrorKcal())
				.appliedAdjustmentKcal(result.appliedAdjustmentKcal())
				.algorithmVersion(result.algorithmVersion())
				.reasonCode(result.reasonCode())
				.reasonDetail(result.reasonDetail())
				.build();

		tdeeAdjustmentEventRepository.save(adjustmentEvent);
	}

	private WeeklyCheckinResponseDto toResponse(WeeklyCheckin weeklyCheckin) {
		return new WeeklyCheckinResponseDto(
				weeklyCheckin.getId(),
				weeklyCheckin.getExpenseTracker().getId(),
				weeklyCheckin.getGoalPlan().getId(),
				weeklyCheckin.getWeekIndex(),
				weeklyCheckin.getWeekStartDate(),
				weeklyCheckin.getWeekEndDate(),
				weeklyCheckin.getAvgWeightKg(),
				weeklyCheckin.getAvgCaloriesKcal(),
				weeklyCheckin.getBodyFatPercent(),
				weeklyCheckin.getWeightChangeFromStartKg(),
				weeklyCheckin.getWeightChangeFromPreviousCheckinKg(),
				weeklyCheckin.getAvgEstimatedTdeeKcal(),
				weeklyCheckin.getDaysWithWeight(),
				weeklyCheckin.getDaysWithCalories(),
				weeklyCheckin.getDaysWithBodyMeasurements(),
				weeklyCheckin.getCreatedDate(),
				weeklyCheckin.getLastModifiedDate()
		);
	}
}