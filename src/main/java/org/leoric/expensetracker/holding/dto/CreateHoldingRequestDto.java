package org.leoric.expensetracker.holding.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateHoldingRequestDto(
		@NotNull(message = "Account ID is required")
		UUID accountId,

		@NotNull(message = "Asset ID is required")
		UUID assetId,

		Long initialAmount
) {
}