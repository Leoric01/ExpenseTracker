package org.leoric.expensetracker.asset.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.dto.AssetResponseDto;
import org.leoric.expensetracker.asset.dto.CreateAssetRequestDto;
import org.leoric.expensetracker.asset.dto.UpdateAssetRequestDto;
import org.leoric.expensetracker.asset.services.interfaces.AssetService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

	private final AssetService assetService;

	@PostMapping
	public ResponseEntity<AssetResponseDto> assetCreate(@Valid @RequestBody CreateAssetRequestDto request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(assetService.assetCreate(request));
	}

	@GetMapping
	public ResponseEntity<Page<AssetResponseDto>> assetFindAll(
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(assetService.assetFindAll(search, pageable));
	}

	@GetMapping("/{assetId}")
	public ResponseEntity<AssetResponseDto> assetFindById(@PathVariable UUID assetId) {
		return ResponseEntity.ok(assetService.assetFindById(assetId));
	}

	@PatchMapping("/{assetId}")
	public ResponseEntity<AssetResponseDto> assetUpdate(
			@PathVariable UUID assetId,
			@Valid @RequestBody UpdateAssetRequestDto request) {
		return ResponseEntity.ok(assetService.assetUpdate(assetId, request));
	}

	@DeleteMapping("/{assetId}")
	public ResponseEntity<Void> assetDeactivate(@PathVariable UUID assetId) {
		assetService.assetDeactivate(assetId);
		return ResponseEntity.noContent().build();
	}
}