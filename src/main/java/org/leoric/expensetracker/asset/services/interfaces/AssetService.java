package org.leoric.expensetracker.asset.services.interfaces;

import org.leoric.expensetracker.asset.dto.AssetResponseDto;
import org.leoric.expensetracker.asset.dto.CreateAssetRequestDto;
import org.leoric.expensetracker.asset.dto.UpdateAssetRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface AssetService {

	AssetResponseDto assetCreate(CreateAssetRequestDto request);

	AssetResponseDto assetFindById(UUID assetId);

	Page<AssetResponseDto> assetFindAll(String search, Pageable pageable);

	AssetResponseDto assetUpdate(UUID assetId, UpdateAssetRequestDto request);

	void assetDeactivate(UUID assetId);
}