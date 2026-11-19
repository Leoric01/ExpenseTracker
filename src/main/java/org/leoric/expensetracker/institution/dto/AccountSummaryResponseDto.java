package org.leoric.expensetracker.institution.dto;

import org.leoric.expensetracker.account.models.constants.AccountType;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingPeriodPerformanceDto;
import org.leoric.expensetracker.holding.dto.InvestmentPerformanceDto;

import java.util.List;
import java.util.UUID;

public record AccountSummaryResponseDto(
		UUID accountId,
		String accountName,
		AccountType accountType,
		String iconUrl,
		String iconColor,
		List<HoldingSummaryResponseDto> holdings,
		long totalBalance,
		Long convertedTotalBalance,
		HoldingPeriodPerformanceDto periodPerformance,
		InvestmentPerformanceDto investmentPerformance
) {
}