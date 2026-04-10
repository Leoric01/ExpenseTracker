package org.leoric.expensetracker.institution.dto;

import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;

import java.time.Instant;
import java.util.List;

public record InstitutionDashboardResponseDto(
		Instant periodFrom,
		Instant periodTo,
		String displayAssetCode,
		Integer displayAssetScale,
		List<WidgetItemResponseDto> widgetOrder,
		List<InstitutionSummaryResponseDto> institutions,
		Long grandTotalConverted
) {
}