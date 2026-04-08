package org.leoric.expensetracker.account.dto;

import org.leoric.expensetracker.account.models.constants.AccountType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponseDto(
		UUID id,
		UUID institutionId,
		String institutionName,
		String name,
		AccountType accountType,
		String description,
		String externalRef,
		String iconUrl,
		String iconColor,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}