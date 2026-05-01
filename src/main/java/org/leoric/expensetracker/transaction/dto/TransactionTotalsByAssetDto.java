package org.leoric.expensetracker.transaction.dto;

public record TransactionTotalsByAssetDto(
		String assetCode,
		Integer assetScale,
		long incomeAmount,
		long expenseAmount,
		long netAmount
) {
}