package org.leoric.expensetracker.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;

public record CreateAssetRequestDto(
		@NotBlank(message = "Code is required")
		String code,

		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Asset type is required")
		AssetType assetType,

		@NotNull(message = "Scale is required")
		@PositiveOrZero(message = "Scale must be zero or positive")
		Integer scale,

		@NotNull(message = "Market data source is required")
		MarketDataSource marketDataSource,

		String marketDataKey
) {
}