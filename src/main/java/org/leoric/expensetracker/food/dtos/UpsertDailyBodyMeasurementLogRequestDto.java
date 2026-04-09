package org.leoric.expensetracker.food.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertDailyBodyMeasurementLogRequestDto(
		LocalDate logDate,
		BigDecimal waistCm,
		BigDecimal neckCm,
		BigDecimal hipCm
) {
}