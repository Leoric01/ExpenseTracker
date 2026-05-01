package org.leoric.expensetracker.category.dto;

public record CategoryMovementAssetTotalsDto(
		String assetCode,
		Integer assetScale,
		long expectedExpenseAmount,
		long expectedIncomeAmount,
		long expectedSavingAmount,
		long actualExpenseAmount,
		long actualIncomeAmount,
		long actualSavingAmount
) {
}