package org.leoric.expensetracker.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.account.models.constants.AccountType;

import java.util.UUID;

public record CreateAccountRequestDto(
		@NotNull(message = "Institution ID is required")
		UUID institutionId,

		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Account type is required")
		AccountType accountType,

		String description,

		String externalRef
) {
}