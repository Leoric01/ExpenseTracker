package org.leoric.expensetracker.category.dto;

import org.leoric.expensetracker.budget.models.constants.PeriodType;

import java.time.Instant;

public record CategoryActiveBudgetPlanEmbed(
		String id,
		String name,
		long amount,
		String assetCode,
		Integer assetScale,
		PeriodType periodType,
		Instant validFrom,
		Instant validTo,
		long alreadySpent
) {
}