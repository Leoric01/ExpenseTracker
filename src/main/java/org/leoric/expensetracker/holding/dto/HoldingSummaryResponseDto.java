package org.leoric.expensetracker.holding.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldingSummaryResponseDto(
		UUID holdingId,
		String accountName,
		String institutionName,
		String assetCode,
		int assetScale,
		Instant periodFrom,
		Instant periodTo,
		long startBalance,
		long endBalance,
		long totalIncome,
		long totalExpense,
		long totalTransferIn,
		long totalTransferOut,
		long difference,
		List<CategoryBreakdownDto> incomeByCategory,
		List<CategoryBreakdownDto> expenseByCategory
) {
}