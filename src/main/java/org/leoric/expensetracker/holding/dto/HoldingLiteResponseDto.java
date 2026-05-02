package org.leoric.expensetracker.holding.dto;

import java.util.UUID;

public record HoldingLiteResponseDto(
		UUID id,
		String institutionName,
		String accountName,
		String assetCode,
		Integer assetScale
) {
}