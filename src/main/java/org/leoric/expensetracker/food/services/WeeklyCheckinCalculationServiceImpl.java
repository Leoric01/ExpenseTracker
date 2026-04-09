package org.leoric.expensetracker.food.services;

import org.leoric.expensetracker.food.dtos.ObservedTdeeCalculationCommand;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinCalculationCommand;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinSnapshot;
import org.leoric.expensetracker.food.models.DailyBodyMeasurementLog;
import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.leoric.expensetracker.food.models.WeeklyCheckin;
import org.leoric.expensetracker.food.services.interfaces.TdeeCalculationService;
import org.leoric.expensetracker.food.services.interfaces.WeeklyCheckinCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class WeeklyCheckinCalculationServiceImpl implements WeeklyCheckinCalculationService {

	private final TdeeCalculationService tdeeCalculationService;

	public WeeklyCheckinCalculationServiceImpl(TdeeCalculationService tdeeCalculationService) {
		this.tdeeCalculationService = tdeeCalculationService;
	}

	@Override
	public WeeklyCheckinSnapshot calculate(WeeklyCheckinCalculationCommand command) {
		validate(command);

		List<DailyNutritionLog> dailyNutritionLogs = command.dailyNutritionLogs() == null
				? List.of()
				: command.dailyNutritionLogs();

		List<DailyBodyMeasurementLog> measurementLogs = command.dailyBodyMeasurementLogs() == null
				? List.of()
				: command.dailyBodyMeasurementLogs();

		List<BigDecimal> weights = dailyNutritionLogs.stream()
				.map(DailyNutritionLog::getWeightKg)
				.filter(Objects::nonNull)
				.toList();

		List<BigDecimal> calories = dailyNutritionLogs.stream()
				.map(DailyNutritionLog::getCaloriesKcal)
				.filter(Objects::nonNull)
				.toList();

		BigDecimal avgWeightKg = average(weights);
		BigDecimal avgCaloriesKcal = average(calories);
		BigDecimal bodyFatPercent = latestBodyFat(measurementLogs);

		BigDecimal weightChangeFromStartKg = avgWeightKg == null
				? null
				: avgWeightKg.subtract(command.startWeightKg()).setScale(2, RoundingMode.HALF_UP);

		BigDecimal weightChangeFromPreviousCheckinKg = calculateWeightChangeFromPrevious(command, avgWeightKg);

		BigDecimal avgEstimatedTdeeKcal = null;
		if (avgCaloriesKcal != null && weightChangeFromPreviousCheckinKg != null) {
			avgEstimatedTdeeKcal = tdeeCalculationService.calculateObservedTdee(
					new ObservedTdeeCalculationCommand(avgCaloriesKcal, weightChangeFromPreviousCheckinKg)
			);
		}

		return new WeeklyCheckinSnapshot(
				command.weekIndex(),
				command.weekStartDate(),
				command.weekEndDate(),
				avgWeightKg,
				avgCaloriesKcal,
				bodyFatPercent,
				weightChangeFromStartKg,
				weightChangeFromPreviousCheckinKg,
				avgEstimatedTdeeKcal,
				weights.size(),
				calories.size(),
				(int) measurementLogs.stream().filter(m -> m.getCalculatedBodyFatPercent() != null).count()
		);
	}

	private BigDecimal calculateWeightChangeFromPrevious(WeeklyCheckinCalculationCommand command, BigDecimal avgWeightKg) {
		if (avgWeightKg == null) {
			return null;
		}

		List<WeeklyCheckin> previous = command.previousWeeklyCheckins() == null
				? List.of()
				: command.previousWeeklyCheckins();

		BigDecimal bigDecimal = avgWeightKg.subtract(command.startWeightKg()).setScale(2, RoundingMode.HALF_UP);
		if (previous.isEmpty()) {
			return bigDecimal;
		}

		WeeklyCheckin last = previous.stream()
				.filter(w -> w.getAvgWeightKg() != null)
				.max(Comparator.comparingInt(WeeklyCheckin::getWeekIndex))
				.orElse(null);

		if (last == null || last.getAvgWeightKg() == null) {
			return bigDecimal;
		}

		return avgWeightKg.subtract(last.getAvgWeightKg()).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal latestBodyFat(List<DailyBodyMeasurementLog> measurementLogs) {
		return measurementLogs.stream()
				.filter(m -> m.getCalculatedBodyFatPercent() != null)
				.max(Comparator.comparing(DailyBodyMeasurementLog::getLogDate))
				.map(DailyBodyMeasurementLog::getCalculatedBodyFatPercent)
				.map(v -> v.setScale(2, RoundingMode.HALF_UP))
				.orElse(null);
	}

	private BigDecimal average(List<BigDecimal> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}

		BigDecimal sum = values.stream()
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
	}

	private void validate(WeeklyCheckinCalculationCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("WeeklyCheckinCalculationCommand cannot be null");
		}
		if (command.weekIndex() == null || command.weekIndex() < 1) {
			throw new IllegalArgumentException("Week index must be at least 1");
		}
		if (command.weekStartDate() == null || command.weekEndDate() == null) {
			throw new IllegalArgumentException("Week start and end dates cannot be null");
		}
		if (command.startWeightKg() == null || command.startWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Start weight must be greater than 0");
		}
	}
}