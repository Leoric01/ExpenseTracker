package org.leoric.expensetracker.institution.dto;

public record FinanceHeaderNativeBalanceRow(
		String assetCode,
		int assetScale,
		long totalMinorUnits
) {
}