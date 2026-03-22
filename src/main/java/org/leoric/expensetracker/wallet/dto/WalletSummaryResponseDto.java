package org.leoric.expensetracker.wallet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WalletSummaryResponseDto(
		UUID walletId,
		String walletName,
		String currencyCode,
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