package org.leoric.expensetracker.holding.dto;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.holding.models.InitialValuationMode;

import java.time.Instant;
import java.util.UUID;

public record CreateHoldingRequestDto(
		@NotNull(message = "Account ID is required")
		UUID accountId,

		@NotNull(message = "Asset ID is required")
		UUID assetId,

		Long initialAmount,
		InitialValuationMode initialValuationMode,
		Long initialValuationAmount,
		UUID initialValuationAssetId,
		Instant initialValuationAt
) {
}