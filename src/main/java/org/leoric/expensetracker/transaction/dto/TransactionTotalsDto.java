package org.leoric.expensetracker.transaction.dto;

import java.util.List;

public record TransactionTotalsDto(
		List<TransactionTotalsByAssetDto> byAsset,
		TransactionConvertedTotalsDto converted
) {
}