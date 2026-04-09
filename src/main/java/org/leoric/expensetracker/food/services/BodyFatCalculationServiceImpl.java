package org.leoric.expensetracker.food.services;

import org.leoric.expensetracker.food.dtos.BodyFatCalculationCommand;
import org.leoric.expensetracker.food.models.constants.BiologicalSex;
import org.leoric.expensetracker.food.services.interfaces.BodyFatCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BodyFatCalculationServiceImpl implements BodyFatCalculationService {

	private static final int SCALE = 2;

	@Override
	public BigDecimal calculate(BodyFatCalculationCommand command) {
		validate(command);

		return switch (command.biologicalSex()) {
			case MALE -> calculateMale(command);
			case FEMALE -> calculateFemale(command);
		};
	}

	private BigDecimal calculateMale(BodyFatCalculationCommand command) {
		double waist = command.waistCm().doubleValue();
		double neck = command.neckCm().doubleValue();
		double height = command.heightCm().doubleValue();

		double result = 86.01 * Math.log10(waist - neck) - 70.041 * Math.log10(height) + 30.3;

		return BigDecimal.valueOf(result).setScale(0, RoundingMode.HALF_UP);
	}

	private BigDecimal calculateFemale(BodyFatCalculationCommand command) {
		if (command.hipCm() == null) {
			throw new IllegalArgumentException("Hip measurement is required for female body fat calculation");
		}

		double waist = command.waistCm().doubleValue();
		double neck = command.neckCm().doubleValue();
		double hip = command.hipCm().doubleValue();
		double height = command.heightCm().doubleValue();

		double result = 495.0 / (1.29579 - 0.35004 * Math.log10(waist + hip - neck) + 0.221 * Math.log10(height)) - 450.0;

		return BigDecimal.valueOf(result).setScale(0, RoundingMode.HALF_UP);
	}

	private void validate(BodyFatCalculationCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("BodyFatCalculationCommand cannot be null");
		}
		if (command.biologicalSex() == null) {
			throw new IllegalArgumentException("Biological sex cannot be null");
		}
		if (command.heightCm() == null || command.heightCm().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Height must be greater than 0");
		}
		if (command.waistCm() == null || command.waistCm().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Waist must be greater than 0");
		}
		if (command.neckCm() == null || command.neckCm().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Neck must be greater than 0");
		}
		if (command.biologicalSex() == BiologicalSex.MALE) {
			if (command.waistCm().compareTo(command.neckCm()) <= 0) {
				throw new IllegalArgumentException("Waist must be greater than neck for male body fat calculation");
			}
		}
		if (command.biologicalSex() == BiologicalSex.FEMALE) {
			if (command.hipCm() == null || command.hipCm().compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalArgumentException("Hip must be greater than 0 for female body fat calculation");
			}
			if (command.waistCm().add(command.hipCm()).compareTo(command.neckCm()) <= 0) {
				throw new IllegalArgumentException("Waist + hip must be greater than neck for female body fat calculation");
			}
		}
	}
}