package org.leoric.expensetracker.holding.dto;

import org.leoric.expensetracker.account.models.constants.AccountType;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.institution.models.constants.InstitutionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HoldingResponseDto(
		UUID id,
		UUID accountId,
		String accountName,
		AccountType accountType,
		UUID institutionId,
		String institutionName,
		InstitutionType institutionType,
		UUID assetId,
		String assetCode,
		String assetName,
		AssetType assetType,
		int assetScale,
		long initialAmount,
		long currentAmount,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}