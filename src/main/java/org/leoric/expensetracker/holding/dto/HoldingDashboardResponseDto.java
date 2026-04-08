package org.leoric.expensetracker.holding.dto;

import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;

import java.time.Instant;
import java.util.List;

public record HoldingDashboardResponseDto(
		Instant periodFrom,
		Instant periodTo,
		List<WidgetItemResponseDto> widgetOrder,
		List<HoldingSummaryResponseDto> holdings
) {
}