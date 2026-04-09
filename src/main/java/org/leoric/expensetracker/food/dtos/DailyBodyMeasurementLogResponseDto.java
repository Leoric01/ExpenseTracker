package org.leoric.expensetracker.food.dtos;

import org.leoric.expensetracker.food.models.constants.BodyFatSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyBodyMeasurementLogResponseDto(
		UUID id,
		UUID expenseTrackerId,
		UUID goalPlanId,
		LocalDate logDate,
		BigDecimal waistCm,
		BigDecimal neckCm,
		BigDecimal hipCm,
		BigDecimal calculatedBodyFatPercent,
		BodyFatSource bodyFatSource,
		Instant createdDate,
		Instant lastModifiedDate
) {
}