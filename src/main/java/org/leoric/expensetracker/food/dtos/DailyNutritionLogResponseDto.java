package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyNutritionLogResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UUID goalPlanId,
		LocalDate logDate,
		BigDecimal weightKg,
		BigDecimal caloriesKcal,
		BigDecimal proteinG,
		BigDecimal fatG,
		BigDecimal carbsG,
		String notes,
		Instant createdDate,
		Instant lastModifiedDate
) {
}