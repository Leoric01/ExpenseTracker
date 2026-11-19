package org.leoric.expensetracker.image.controllers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.image.services.LocalFileStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LocalFileController {
	private final LocalFileStorage storage;

	@GetMapping("/images/{namespace}/{group}/{name:.+}")
	public ResponseEntity<Resource> read(@PathVariable String namespace, @PathVariable String group,
			@PathVariable String name) {
		LocalFileStorage.StoredFile file;
		try {
			file = storage.read(namespace, group, name);
		} catch (IllegalArgumentException ex) {
			throw new EntityNotFoundException("File not found");
		}
		var response = ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.cacheControl(CacheControl.noStore())
				.header("X-Content-Type-Options", "nosniff");
		if (file.contentType().equals("application/pdf")) {
			response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
		}
		return response.body(file.resource());
	}
}
