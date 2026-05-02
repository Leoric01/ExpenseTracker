package org.leoric.expensetracker.category.dto;

import java.math.BigDecimal;

public record CategoryMovementAssetTotalsDto(
		String assetCode,
		Integer assetScale,
		BigDecimal exchangeRate,
		long expectedExpenseAmount,
		long expectedIncomeAmount,
		long expectedSavingAmount,
		long actualExpenseAmount,
		long actualIncomeAmount,
		long actualSavingAmount
) {
}