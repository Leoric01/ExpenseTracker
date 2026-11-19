package org.leoric.expensetracker.holding.dto;

import java.time.Instant;

public record InvestmentPerformanceDto(
		Long realized,
		Long unrealized,
		Long total,
		boolean estimated,
		boolean artificialAllocation,
		String unavailableReason,
		Instant asOf
) {
}
