package org.leoric.expensetracker.category.dto;

import java.time.Instant;
import java.util.List;

public record CategoryMovementSummaryResponseDto(
		Instant dateFrom,
		Instant dateTo,
		List<CategoryMovementAssetTotalsDto> nativeByAsset,
		CategoryMovementConvertedTotalsDto convertedTotals
) {
}