package org.leoric.expensetracker.account.dto;

import org.leoric.expensetracker.account.models.constants.AccountType;

public record UpdateAccountRequestDto(
		String name,
		AccountType accountType,
		String description,
		String externalRef
) {
}