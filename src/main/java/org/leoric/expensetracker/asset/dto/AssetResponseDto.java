package org.leoric.expensetracker.asset.dto;

import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetResponseDto(
		UUID id,
		String code,
		String name,
		AssetType assetType,
		int scale,
		MarketDataSource marketDataSource,
		String marketDataKey,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}