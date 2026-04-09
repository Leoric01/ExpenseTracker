package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BiologicalSex;
import org.leoric.expensetracker.food.models.constants.UnitSystem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NutritionProfileResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UnitSystem preferredUnitSystem,
		BiologicalSex biologicalSex,
		BigDecimal heightCm,
		BigDecimal activityMultiplier,
		boolean bodyFatAutoCalculationEnabled,
		Instant createdDate,
		Instant lastModifiedDate
) {
}