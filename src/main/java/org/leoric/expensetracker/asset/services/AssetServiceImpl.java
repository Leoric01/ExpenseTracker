package org.leoric.expensetracker.asset.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.dto.AssetResponseDto;
import org.leoric.expensetracker.asset.dto.CreateAssetRequestDto;
import org.leoric.expensetracker.asset.dto.UpdateAssetRequestDto;
import org.leoric.expensetracker.asset.mapstruct.AssetMapper;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.asset.services.interfaces.AssetService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

	private final AssetRepository assetRepository;
	private final AssetMapper assetMapper;

	@Override
	@Transactional
	public AssetResponseDto assetCreate(CreateAssetRequestDto request) {
		if (assetRepository.existsByCodeIgnoreCase(request.code())) {
			throw new OperationNotPermittedException("Asset with code '%s' already exists".formatted(request.code()));
		}

		Asset asset = Asset.builder()
				.code(request.code().toUpperCase())
				.name(request.name())
				.assetType(request.assetType())
				.scale(request.scale())
				.marketDataSource(request.marketDataSource())
				.marketDataKey(request.marketDataKey())
				.build();

		asset = assetRepository.save(asset);
		log.info("Created asset '{}' ({})", asset.getCode(), asset.getAssetType());
		return assetMapper.toResponse(asset);
	}

	@Override
	@Transactional(readOnly = true)
	public AssetResponseDto assetFindById(UUID assetId) {
		return assetMapper.toResponse(getAssetOrThrow(assetId));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AssetResponseDto> assetFindAll(String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return assetRepository.findByActiveTrueWithSearch(search, pageable)
					.map(assetMapper::toResponse);
		}
		return assetRepository.findByActiveTrue(pageable)
				.map(assetMapper::toResponse);
	}

	@Override
	@Transactional
	public AssetResponseDto assetUpdate(UUID assetId, UpdateAssetRequestDto request) {
		Asset asset = getAssetOrThrow(assetId);
		assetMapper.updateFromDto(request, asset);
		asset = assetRepository.save(asset);
		log.info("Updated asset '{}'", asset.getCode());
		return assetMapper.toResponse(asset);
	}

	@Override
	@Transactional
	public void assetDeactivate(UUID assetId) {
		Asset asset = getAssetOrThrow(assetId);
		if (!asset.isActive()) {
			throw new OperationNotPermittedException("Asset is already deactivated");
		}
		asset.setActive(false);
		assetRepository.save(asset);
		log.info("Deactivated asset '{}'", asset.getCode());
	}

	private Asset getAssetOrThrow(UUID assetId) {
		return assetRepository.findById(assetId)
				.orElseThrow(() -> new EntityNotFoundException("Asset not found"));
	}
}