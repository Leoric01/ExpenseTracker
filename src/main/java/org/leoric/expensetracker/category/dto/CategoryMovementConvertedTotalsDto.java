package org.leoric.expensetracker.category.dto;

public record CategoryMovementConvertedTotalsDto(
		String targetAssetCode,
		Integer targetAssetScale,
		Long expectedExpenseAmount,
		Long expectedIncomeAmount,
		Long expectedSavingAmount,
		Long actualExpenseAmount,
		Long actualIncomeAmount,
		Long actualSavingAmount
) {
}