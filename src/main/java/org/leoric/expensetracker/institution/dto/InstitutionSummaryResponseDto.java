package org.leoric.expensetracker.institution.dto;

import org.leoric.expensetracker.institution.models.constants.InstitutionType;

import java.util.List;
import java.util.UUID;

public record InstitutionSummaryResponseDto(
		UUID institutionId,
		String institutionName,
		InstitutionType institutionType,
		String iconUrl,
		String iconColor,
		List<AccountSummaryResponseDto> accounts,
		long totalBalance,
		Long convertedTotalBalance
) {
}