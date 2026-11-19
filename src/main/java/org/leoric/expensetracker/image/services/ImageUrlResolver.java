package org.leoric.expensetracker.image.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.repositories.PendingImageCleanupRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ImageUrlResolver {
	private final PendingImageCleanupRepository repository;

	public String resolve(String url) {
		var visited = new HashSet<String>();
		while (visited.add(url)) {
			var mapping = repository.findFirstBySourceUrl(url);
			if (mapping.isEmpty()) return url;
			url = mapping.get().getDestinationUrl();
		}
		throw new OperationNotPermittedException("Cycle detected in migrated image URLs");
	}
}
