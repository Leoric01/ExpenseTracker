package org.leoric.expensetracker.food.services;

import org.leoric.expensetracker.food.dtos.MacroCalculationCommand;
import org.leoric.expensetracker.food.dtos.MacroTargets;
import org.leoric.expensetracker.food.models.constants.CarbStrategy;
import org.leoric.expensetracker.food.models.constants.FatStrategy;
import org.leoric.expensetracker.food.models.constants.ProteinStrategy;
import org.leoric.expensetracker.food.services.interfaces.MacroCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.leoric.expensetracker.food.utility.NutritionConstants.FAT_RATIO_HIGHER_BF;
import static org.leoric.expensetracker.food.utility.NutritionConstants.FAT_RATIO_LEAN;
import static org.leoric.expensetracker.food.utility.NutritionConstants.PROTEIN_MULTIPLIER_HIGH_BF;
import static org.leoric.expensetracker.food.utility.NutritionConstants.PROTEIN_MULTIPLIER_LEAN;
import static org.leoric.expensetracker.food.utility.NutritionConstants.PROTEIN_MULTIPLIER_MID;

@Service
public class MacroCalculationServiceImpl implements MacroCalculationService {

	private static final BigDecimal KCAL_PER_GRAM_PROTEIN = new BigDecimal("4");
	private static final BigDecimal KCAL_PER_GRAM_CARB = new BigDecimal("4");
	private static final BigDecimal KCAL_PER_GRAM_FAT = new BigDecimal("9");

	@Override
	public MacroTargets calculate(MacroCalculationCommand command) {
		validate(command);

		BigDecimal proteinG = calculateProtein(command);
		BigDecimal fatG = calculateFat(command);

		BigDecimal remainingCalories = command.targetCaloriesKcal()
				.subtract(proteinG.multiply(KCAL_PER_GRAM_PROTEIN))
				.subtract(fatG.multiply(KCAL_PER_GRAM_FAT));

		BigDecimal carbsG = remainingCalories.divide(KCAL_PER_GRAM_CARB, 2, RoundingMode.HALF_UP);

		if (carbsG.compareTo(BigDecimal.ZERO) < 0) {
			carbsG = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}

		return new MacroTargets(
				proteinG.setScale(2, RoundingMode.HALF_UP),
				fatG.setScale(2, RoundingMode.HALF_UP),
				carbsG.setScale(2, RoundingMode.HALF_UP)
		);
	}

	private BigDecimal calculateProtein(MacroCalculationCommand command) {
		if (command.proteinStrategy() == ProteinStrategy.CUSTOM) {
			throw new UnsupportedOperationException("CUSTOM protein strategy is not implemented yet");
		}

		BigDecimal multiplier;

		if (command.bodyFatPercent().compareTo(new BigDecimal("20")) < 0) {
			multiplier = PROTEIN_MULTIPLIER_LEAN;
		} else if (command.bodyFatPercent().compareTo(new BigDecimal("25")) <= 0) {
			multiplier = PROTEIN_MULTIPLIER_MID;
		} else {
			multiplier = PROTEIN_MULTIPLIER_HIGH_BF;
		}

		if (command.proteinStrategy() == ProteinStrategy.HIGH) {
			multiplier = multiplier.multiply(new BigDecimal("1.10"));
		}

		return command.weightKg().multiply(multiplier);
	}

	private BigDecimal calculateFat(MacroCalculationCommand command) {
		if (command.fatStrategy() == FatStrategy.CUSTOM) {
			throw new UnsupportedOperationException("CUSTOM fat strategy is not implemented yet");
		}

		BigDecimal fatRatio = command.bodyFatPercent().compareTo(new BigDecimal("25")) < 0
				? FAT_RATIO_LEAN
				: FAT_RATIO_HIGHER_BF;

		if (command.fatStrategy() == FatStrategy.HIGHER) {
			fatRatio = fatRatio.add(new BigDecimal("0.03"));
		}

		return command.targetCaloriesKcal()
				.multiply(fatRatio)
				.divide(KCAL_PER_GRAM_FAT, 2, RoundingMode.HALF_UP);
	}

	private void validate(MacroCalculationCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("MacroCalculationCommand cannot be null");
		}
		if (command.weightKg() == null || command.weightKg().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Weight must be greater than 0");
		}
		if (command.bodyFatPercent() == null || command.bodyFatPercent().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Body fat percent must be 0 or greater");
		}
		if (command.targetCaloriesKcal() == null || command.targetCaloriesKcal().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Target calories must be greater than 0");
		}
		if (command.proteinStrategy() == null) {
			throw new IllegalArgumentException("Protein strategy cannot be null");
		}
		if (command.fatStrategy() == null) {
			throw new IllegalArgumentException("Fat strategy cannot be null");
		}
		if (command.carbStrategy() == null) {
			throw new IllegalArgumentException("Carb strategy cannot be null");
		}
		if (command.carbStrategy() != CarbStrategy.REMAINDER) {
			throw new UnsupportedOperationException("Only REMAINDER carb strategy is implemented");
		}
	}
}