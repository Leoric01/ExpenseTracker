package org.leoric.expensetracker.food.services;

import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentCommand;
import org.leoric.expensetracker.food.dtos.AdaptiveNutritionAdjustmentResult;
import org.leoric.expensetracker.food.dtos.MacroCalculationCommand;
import org.leoric.expensetracker.food.dtos.MacroTargets;
import org.leoric.expensetracker.food.models.WeeklyCheckin;
import org.leoric.expensetracker.food.services.interfaces.AdaptiveNutritionAdjustmentService;
import org.leoric.expensetracker.food.services.interfaces.MacroCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import static org.leoric.expensetracker.food.utility.NutritionConstants.ALGORITHM_VERSION;
import static org.leoric.expensetracker.food.utility.NutritionConstants.DAYS_PER_WEEK;
import static org.leoric.expensetracker.food.utility.NutritionConstants.KCAL_PER_KG;
import static org.leoric.expensetracker.food.utility.NutritionConstants.MAX_ADAPTIVE_ADJUSTMENT_KCAL;

@Service
public class AdaptiveNutritionAdjustmentServiceImpl implements AdaptiveNutritionAdjustmentService {

	private static final BigDecimal MIN_WEEKS_REQUIRED = new BigDecimal("4");

	private final MacroCalculationService macroCalculationService;

	public AdaptiveNutritionAdjustmentServiceImpl(MacroCalculationService macroCalculationService) {
		this.macroCalculationService = macroCalculationService;
	}

	@Override
	public AdaptiveNutritionAdjustmentResult adjust(AdaptiveNutritionAdjustmentCommand command) {
		validate(command);

		List<WeeklyCheckin> usableCheckins = command.weeklyCheckins().stream()
				.filter(w -> w.getAvgWeightKg() != null)
				.filter(w -> w.getAvgCaloriesKcal() != null)
				.sorted(Comparator.comparingInt(WeeklyCheckin::getWeekIndex))
				.toList();

		if (usableCheckins.size() < MIN_WEEKS_REQUIRED.intValue()) {
			throw new IllegalArgumentException("At least 4 weekly checkins are required for adaptive adjustment");
		}

		WeeklyCheckin latest = usableCheckins.get(usableCheckins.size() - 1);
		WeeklyCheckin reference = usableCheckins.get(usableCheckins.size() - 2);

		BigDecimal observedWeightChangeKg = latest.getAvgWeightKg()
				.subtract(reference.getAvgWeightKg())
				.setScale(3, RoundingMode.HALF_UP);

		BigDecimal expectedWeightChangeKg = command.goalPlan()
				.getTargetWeeklyWeightChangeKg()
				.setScale(3, RoundingMode.HALF_UP);

		BigDecimal weeklyWeightErrorKg = observedWeightChangeKg.subtract(expectedWeightChangeKg);

		BigDecimal estimatedCalorieErrorKcal = weeklyWeightErrorKg
				.multiply(KCAL_PER_KG)
				.divide(DAYS_PER_WEEK, 2, RoundingMode.HALF_UP);

		BigDecimal appliedAdjustmentKcal = estimatedCalorieErrorKcal.negate();
		if (appliedAdjustmentKcal.compareTo(MAX_ADAPTIVE_ADJUSTMENT_KCAL) > 0) {
			appliedAdjustmentKcal = MAX_ADAPTIVE_ADJUSTMENT_KCAL;
		}
		if (appliedAdjustmentKcal.compareTo(MAX_ADAPTIVE_ADJUSTMENT_KCAL.negate()) < 0) {
			appliedAdjustmentKcal = MAX_ADAPTIVE_ADJUSTMENT_KCAL.negate();
		}

		BigDecimal previousTargetCaloriesKcal = command.currentNutritionTarget().getTargetCaloriesKcal();
		BigDecimal newTargetCaloriesKcal = previousTargetCaloriesKcal
				.add(appliedAdjustmentKcal)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal baselineTdeeKcal = command.currentNutritionTarget().getBaselineTdeeKcal();
		BigDecimal calorieAdjustmentKcal = newTargetCaloriesKcal
				.subtract(baselineTdeeKcal)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal bodyFatPercent = latest.getBodyFatPercent() != null
				? latest.getBodyFatPercent()
				: defaultBodyFat(command);

		MacroTargets macroTargets = macroCalculationService.calculate(
				new MacroCalculationCommand(
						latest.getAvgWeightKg(),
						bodyFatPercent,
						newTargetCaloriesKcal,
						command.goalPlan().getProteinStrategy(),
						command.goalPlan().getFatStrategy(),
						command.goalPlan().getCarbStrategy()
				)
		);

		String reasonCode = "ADAPTIVE_ADJUSTMENT";
		String reasonDetail = "Observed weekly weight change %s kg vs expected %s kg"
				.formatted(observedWeightChangeKg.toPlainString(), expectedWeightChangeKg.toPlainString());

		return new AdaptiveNutritionAdjustmentResult(
				previousTargetCaloriesKcal,
				newTargetCaloriesKcal,
				observedWeightChangeKg,
				expectedWeightChangeKg,
				estimatedCalorieErrorKcal,
				appliedAdjustmentKcal,
				baselineTdeeKcal,
				calorieAdjustmentKcal,
				newTargetCaloriesKcal,
				macroTargets.proteinG(),
				macroTargets.fatG(),
				macroTargets.carbsG(),
				ALGORITHM_VERSION,
				reasonCode,
				reasonDetail
		);
	}

	private BigDecimal defaultBodyFat(AdaptiveNutritionAdjustmentCommand command) {
		if (command.goalPlan().getStartBodyFatPercent() != null) {
			return command.goalPlan().getStartBodyFatPercent();
		}
		return BigDecimal.ZERO;
	}

	private void validate(AdaptiveNutritionAdjustmentCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("AdaptiveNutritionAdjustmentCommand cannot be null");
		}
		if (command.goalPlan() == null) {
			throw new IllegalArgumentException("Goal plan cannot be null");
		}
		if (command.currentNutritionTarget() == null) {
			throw new IllegalArgumentException("Current nutrition target cannot be null");
		}
		if (command.weeklyCheckins() == null || command.weeklyCheckins().isEmpty()) {
			throw new IllegalArgumentException("Weekly checkins cannot be null or empty");
		}
	}
}