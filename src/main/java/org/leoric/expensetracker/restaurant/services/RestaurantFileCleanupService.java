package org.leoric.expensetracker.restaurant.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.image.services.ImageStorageMigrationService;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.restaurant.models.RestaurantFileCleanup;
import org.leoric.expensetracker.restaurant.repositories.RestaurantFileCleanupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RestaurantFileCleanupService {
	private final RestaurantFileCleanupRepository repository;
	private final ImageStorageMigrationService migrationService;
	private final ImageService imageService;

	@Transactional
	public void enqueue(String url) {
		repository.save(new RestaurantFileCleanup(url));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enqueueAfterRollback(String url) {
		repository.saveAndFlush(new RestaurantFileCleanup(url));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public long process() {
		for (var item : repository.findAll()) {
			try {
				if (migrationService.stillReferenced(item.getImageUrl())) {
					log.info("Restaurant file remains referenced; cleanup deferred: {}", item.getImageUrl());
				} else if (imageService.deleteImageByUrl(item.getImageUrl())) {
					repository.delete(item);
				} else {
					log.warn("Restaurant file cleanup remains pending: {}", item.getImageUrl());
				}
			} catch (RuntimeException e) {
				log.warn("Restaurant file cleanup remains pending: {}", item.getImageUrl(), e);
			}
		}
		repository.flush();
		return repository.count();
	}
}
