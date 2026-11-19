package org.leoric.expensetracker.holding.dto;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.holding.models.InitialValuationMode;

import java.time.Instant;
import java.util.UUID;

public record UpdateInitialValuationRequestDto(
		@NotNull InitialValuationMode mode,
		Long amount,
		UUID assetId,
		Instant valuationAt
) {
}
