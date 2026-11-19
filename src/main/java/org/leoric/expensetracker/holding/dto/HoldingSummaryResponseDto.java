package org.leoric.expensetracker.holding.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.leoric.expensetracker.exchangerate.services.ExchangeRateMetadata;

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
		List<CategoryBreakdownDto> expenseByCategory,
		// --- converted to display currency (null if same or no display asset set) ---
		Long convertedStartBalance,
		Long convertedEndBalance,
		BigDecimal exchangeRate,
		HoldingPeriodPerformanceDto periodPerformance,
		InvestmentPerformanceDto investmentPerformance,
		ExchangeRateMetadata exchangeRateMetadata
) {
	public HoldingSummaryResponseDto(
			UUID holdingId, String accountName, String institutionName, String assetCode, int assetScale,
			Instant periodFrom, Instant periodTo, long startBalance, long endBalance, long totalIncome,
			long totalExpense, long totalTransferIn, long totalTransferOut, long difference,
			List<CategoryBreakdownDto> incomeByCategory, List<CategoryBreakdownDto> expenseByCategory,
			Long convertedStartBalance, Long convertedEndBalance, BigDecimal exchangeRate) {
		this(holdingId, accountName, institutionName, assetCode, assetScale, periodFrom, periodTo,
				startBalance, endBalance, totalIncome, totalExpense, totalTransferIn, totalTransferOut,
				difference, incomeByCategory, expenseByCategory, convertedStartBalance, convertedEndBalance,
				exchangeRate, null, null, null);
	}
}