package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeeklyCheckinResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UUID goalPlanId,
		Integer weekIndex,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		BigDecimal avgWeightKg,
		BigDecimal avgCaloriesKcal,
		BigDecimal bodyFatPercent,
		BigDecimal weightChangeFromStartKg,
		BigDecimal weightChangeFromPreviousCheckinKg,
		BigDecimal avgEstimatedTdeeKcal,
		Integer daysWithWeight,
		Integer daysWithCalories,
		Integer daysWithBodyMeasurements,
		Instant createdDate,
		Instant lastModifiedDate
) {
}