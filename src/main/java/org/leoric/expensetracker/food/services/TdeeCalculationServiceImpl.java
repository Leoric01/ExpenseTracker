package org.leoric.expensetracker.food.services;

import org.leoric.expensetracker.food.dtos.ObservedTdeeCalculationCommand;
import org.leoric.expensetracker.food.dtos.TdeeCalculationCommand;
import org.leoric.expensetracker.food.services.interfaces.TdeeCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.leoric.expensetracker.food.utility.NutritionConstants.DAYS_PER_WEEK;
import static org.leoric.expensetracker.food.utility.NutritionConstants.KCAL_PER_KG;

@Service
public class TdeeCalculationServiceImpl implements TdeeCalculationService {

	private static final int SCALE = 2;

	@Override
	public BigDecimal calculateBaselineTdee(TdeeCalculationCommand command) {
		validateBaseline(command);

		BigDecimal leanBodyMassKg = command.weightKg()
				.multiply(BigDecimal.valueOf(100).subtract(command.bodyFatPercent()))
				.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

		BigDecimal bmr = BigDecimal.valueOf(370)
				.add(leanBodyMassKg.multiply(new BigDecimal("21.6")));

		return bmr.multiply(command.activityMultiplier())
				.setScale(SCALE, RoundingMode.HALF_UP);
	}

	@Override
	public BigDecimal calculateObservedTdee(ObservedTdeeCalculationCommand command) {
		validateObserved(command);

		BigDecimal dailyEnergyShift = command.observedWeeklyWeightChangeKg()
				.multiply(KCAL_PER_KG)
				.divide(DAYS_PER_WEEK, 10, RoundingMode.HALF_UP);

		return command.averageDailyCaloriesKcal()
				.subtract(dailyEnergyShift)
				.setScale(SCALE, RoundingMode.HALF_UP);
	}

	private void validateBaseline(TdeeCalculationCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("TdeeCalculationCommand cannot be null");
		}
		if (command.weightKg() == null || command.weightKg().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Weight must be greater than 0");
		}
		if (command.bodyFatPercent() == null || command.bodyFatPercent().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Body fat percent must be 0 or greater");
		}
		if (command.bodyFatPercent().compareTo(BigDecimal.valueOf(100)) >= 0) {
			throw new IllegalArgumentException("Body fat percent must be below 100");
		}
		if (command.activityMultiplier() == null || command.activityMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Activity multiplier must be greater than 0");
		}
	}

	private void validateObserved(ObservedTdeeCalculationCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("ObservedTdeeCalculationCommand cannot be null");
		}
		if (command.averageDailyCaloriesKcal() == null || command.averageDailyCaloriesKcal().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Average daily calories must be greater than 0");
		}
		if (command.observedWeeklyWeightChangeKg() == null) {
			throw new IllegalArgumentException("Observed weekly weight change cannot be null");
		}
	}
}