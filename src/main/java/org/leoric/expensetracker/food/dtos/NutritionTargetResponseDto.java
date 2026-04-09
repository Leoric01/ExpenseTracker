package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NutritionTargetResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UUID goalPlanId,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		BigDecimal baselineTdeeKcal,
		BigDecimal calorieAdjustmentKcal,
		BigDecimal targetCaloriesKcal,
		BigDecimal targetProteinG,
		BigDecimal targetFatG,
		BigDecimal targetCarbsG,
		String algorithmVersion,
		String reasonCode,
		String reasonDetail,
		boolean manualOverride,
		Instant createdDate,
		Instant lastModifiedDate
) {
}