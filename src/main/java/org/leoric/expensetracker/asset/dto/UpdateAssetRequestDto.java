package org.leoric.expensetracker.asset.dto;

import jakarta.validation.constraints.PositiveOrZero;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;

public record UpdateAssetRequestDto(
		String name,
		AssetType assetType,
		@PositiveOrZero(message = "Scale must be zero or positive")
		Integer scale,
		MarketDataSource marketDataSource,
		String marketDataKey
) {
}