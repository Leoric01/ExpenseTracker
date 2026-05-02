package org.leoric.expensetracker.food.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.NutritionDashboardCaloriePointDto;
import org.leoric.expensetracker.food.dtos.NutritionDashboardCheckinPointDto;
import org.leoric.expensetracker.food.dtos.NutritionDashboardCurrentTargetDto;
import org.leoric.expensetracker.food.dtos.NutritionDashboardLatestCheckinDto;
import org.leoric.expensetracker.food.dtos.NutritionDashboardResponseDto;
import org.leoric.expensetracker.food.dtos.NutritionDashboardWeightPointDto;
import org.leoric.expensetracker.food.dtos.NutritionSummaryMonthDto;
import org.leoric.expensetracker.food.dtos.NutritionSummaryResponseDto;
import org.leoric.expensetracker.food.dtos.NutritionSummaryWeekDto;
import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionTarget;
import org.leoric.expensetracker.food.models.WeeklyCheckin;
import org.leoric.expensetracker.food.repositories.DailyNutritionLogRepository;
import org.leoric.expensetracker.food.repositories.WeeklyCheckinRepository;
import org.leoric.expensetracker.food.services.interfaces.NutritionDashboardService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class NutritionDashboardServiceImpl implements NutritionDashboardService {

	private final DailyNutritionLogRepository dailyNutritionLogRepository;
	private final WeeklyCheckinRepository weeklyCheckinRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;

	@Override
	@Transactional(readOnly = true)
	public NutritionDashboardResponseDto nutritionDashboard(User currentUser, UUID trackerId, LocalDate from, LocalDate to) {
		GoalPlan activeGoalPlan = nutritionDomainLookupService.getActiveGoalPlanOrThrow(trackerId);
		NutritionTarget currentTarget = nutritionDomainLookupService.getCurrentNutritionTargetOrThrow(trackerId);

		List<DailyNutritionLog> logs = dailyNutritionLogRepository.findByExpenseTrackerIdAndLogDateBetweenOrderByLogDateAsc(trackerId, from, to);
		List<WeeklyCheckin> weeklyCheckins = weeklyCheckinRepository.findByGoalPlanIdAndWeekRange(activeGoalPlan.getId(), from, to);

		WeeklyCheckin latestCheckin = weeklyCheckins.isEmpty()
				? null
				: weeklyCheckins.getLast();

		return new NutritionDashboardResponseDto(
				trackerId,
				activeGoalPlan.getId(),
				from,
				to,
				new NutritionDashboardCurrentTargetDto(
						currentTarget.getBaselineTdeeKcal(),
						currentTarget.getCalorieAdjustmentKcal(),
						currentTarget.getTargetCaloriesKcal(),
						currentTarget.getTargetProteinG(),
						currentTarget.getTargetFatG(),
						currentTarget.getTargetCarbsG(),
						currentTarget.getEffectiveFrom(),
						currentTarget.getEffectiveTo(),
						currentTarget.isManualOverride()
				),
				latestCheckin == null ? null : new NutritionDashboardLatestCheckinDto(
						latestCheckin.getWeekIndex(),
						latestCheckin.getWeekStartDate(),
						latestCheckin.getWeekEndDate(),
						latestCheckin.getAvgWeightKg(),
						latestCheckin.getAvgCaloriesKcal(),
						latestCheckin.getBodyFatPercent(),
						latestCheckin.getWeightChangeFromStartKg(),
						latestCheckin.getWeightChangeFromPreviousCheckinKg(),
						latestCheckin.getAvgEstimatedTdeeKcal(),
						latestCheckin.getDaysWithWeight(),
						latestCheckin.getDaysWithCalories(),
						latestCheckin.getDaysWithBodyMeasurements()
				),
				logs.stream()
						.filter(l -> l.getWeightKg() != null)
						.map(l -> new NutritionDashboardWeightPointDto(l.getLogDate(), l.getWeightKg()))
						.toList(),
				logs.stream()
						.filter(l -> l.getCaloriesKcal() != null)
						.map(l -> new NutritionDashboardCaloriePointDto(l.getLogDate(), l.getCaloriesKcal()))
						.toList(),
				weeklyCheckins.stream()
						.map(w -> new NutritionDashboardCheckinPointDto(
								w.getWeekIndex(),
								w.getWeekStartDate(),
								w.getWeekEndDate(),
								w.getAvgWeightKg(),
								w.getAvgCaloriesKcal(),
								w.getBodyFatPercent(),
								w.getAvgEstimatedTdeeKcal()
						))
						.toList()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public NutritionSummaryResponseDto nutritionSummary(User currentUser, UUID trackerId, UUID goalPlanId) {
		GoalPlan goalPlan = nutritionDomainLookupService.getGoalPlanOrThrow(trackerId, goalPlanId);
		List<WeeklyCheckin> weeklyCheckins = weeklyCheckinRepository.findByGoalPlanIdOrderByWeekIndexAsc(goalPlanId);

		Map<YearMonth, List<WeeklyCheckin>> checkinsByMonth = weeklyCheckins.stream()
				.collect(Collectors.groupingBy(w -> YearMonth.from(w.getWeekStartDate()), LinkedHashMap::new, toList()));

		List<NutritionSummaryMonthDto> months = checkinsByMonth.entrySet().stream()
				.map(entry -> {
					List<WeeklyCheckin> monthCheckins = entry.getValue();
					BigDecimal actualWeightChangeKg = calculateMonthActualWeightChange(monthCheckins);
					BigDecimal goalWeightChangeKg = goalPlan.getTargetWeeklyWeightChangeKg()
							.multiply(BigDecimal.valueOf(monthCheckins.size()))
									.setScale(2, RoundingMode.HALF_UP);
					return new NutritionSummaryMonthDto(entry.getKey(), actualWeightChangeKg, goalWeightChangeKg);
				}).toList();

		return new NutritionSummaryResponseDto(
				trackerId,
				goalPlan.getId(),
				goalPlan.getName(),
				goalPlan.getGoalType(),
				goalPlan.getStartDate(),
				goalPlan.getEndDate(),
				goalPlan.getStartWeightKg(),
				goalPlan.getStartBodyFatPercent(),
				goalPlan.getTargetWeeklyWeightChangeKg(),
				weeklyCheckins.stream()
						.map(w -> new NutritionSummaryWeekDto(
								w.getWeekIndex(),
								w.getWeekStartDate(),
								w.getWeekEndDate(),
								w.getAvgWeightKg(),
								w.getAvgCaloriesKcal(),
								w.getBodyFatPercent(),
								w.getWeightChangeFromStartKg(),
								w.getWeightChangeFromPreviousCheckinKg(),
								w.getAvgEstimatedTdeeKcal()
						))
						.toList(),
				months
		);
	}

	private BigDecimal calculateMonthActualWeightChange(List<WeeklyCheckin> monthCheckins) {
		List<WeeklyCheckin> usable = monthCheckins.stream()
				.filter(w -> w.getAvgWeightKg() != null)
				.toList();

		if (usable.size() < 2) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}

		BigDecimal first = usable.getFirst().getAvgWeightKg();
		BigDecimal last = usable.getLast().getAvgWeightKg();

		return last.subtract(first).setScale(2, RoundingMode.HALF_UP);
	}
}