package org.leoric.expensetracker.image.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.image.dtos.ImageStorageResponse;
import org.leoric.expensetracker.image.dtos.ImageStorageUpdateRequest;
import org.leoric.expensetracker.image.services.ImageStorageMigrationService;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile/image-storage")
@RequiredArgsConstructor
@Secured("ADMIN")
public class ImageStorageController {
	private final ImageStorageMigrationService service;

	@GetMapping
	public ImageStorageResponse get() {
		return service.status(0, java.util.List.of());
	}

	@PutMapping
	public ImageStorageResponse update(@Valid @RequestBody ImageStorageUpdateRequest request) {
		return service.change(request.provider(), request.migrate());
	}
}
