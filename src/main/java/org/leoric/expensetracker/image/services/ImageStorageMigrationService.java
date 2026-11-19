package org.leoric.expensetracker.image.services;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.dtos.ImageStorageResponse;
import org.leoric.expensetracker.image.models.ImageStorageProvider;
import org.leoric.expensetracker.image.models.ImageStorageSetting;
import org.leoric.expensetracker.image.models.PendingImageCleanup;
import org.leoric.expensetracker.image.repositories.ImageStorageSettingRepository;
import org.leoric.expensetracker.image.repositories.PendingImageCleanupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ImageStorageMigrationService {
	private static final int MAX_BYTES = 10 * 1024 * 1024;
	private static final List<Reference> REFERENCES = List.of(
			new Reference("Account", "iconUrl", "accounts", true),
			new Reference("Institution", "iconUrl", "institutions", true),
			new Reference("Category", "iconUrl", "categories", true),
			new Reference("Todo", "imageUrl", "todo", true),
			new Reference("KanbanCard", "imageUrl", "kanban", true),
			new Reference("WorkoutExercise", "imageUrl", "workout", true),
			new Reference("RestaurantPhoto", "imageUrl", "restaurants", true),
			new Reference("RestaurantItemPhoto", "imageUrl", "restaurants", true),
			new Reference("TransactionAttachment", "fileUrl", "attachments", false));

	private final EntityManager entityManager;
	private final TransactionTemplate transactions;
	private final ImageStorageSettingRepository settings;
	private final PendingImageCleanupRepository cleanup;
	private final ImageServiceImpl images;
	private final LocalFileStorage local;
	private final CloudinaryFileAccess cloud;
	private final ImageUrlResolver resolver;

	public ImageStorageMigrationService(EntityManager entityManager, PlatformTransactionManager transactionManager,
			ImageStorageSettingRepository settings, PendingImageCleanupRepository cleanup, ImageServiceImpl images,
			LocalFileStorage local, CloudinaryFileAccess cloud, ImageUrlResolver resolver) {
		this.entityManager = entityManager;
		this.transactions = new TransactionTemplate(transactionManager);
		this.settings = settings;
		this.cleanup = cleanup;
		this.images = images;
		this.local = local;
		this.cloud = cloud;
		this.resolver = resolver;
	}

	public ImageStorageResponse status(int migrated, List<String> problems) {
		Map<String, Reference> references = findReferences();
		long localCount = references.keySet().stream().filter(this::isLocal).count();
		long cloudCount = references.keySet().stream().filter(this::isCloudinary).count();
		return new ImageStorageResponse(images.currentProvider(), cloud.isConfigured(), localCount, cloudCount,
				cleanup.countByCleanedFalse(), migrated, List.copyOf(problems));
	}

	public ImageStorageResponse change(ImageStorageProvider provider, boolean migrate) {
		if (provider == ImageStorageProvider.CLOUDINARY && !cloud.isConfigured()) {
			throw new OperationNotPermittedException("Cloudinary credentials are not configured");
		}
		settings.saveAndFlush(new ImageStorageSetting(provider));
		List<String> problems = new ArrayList<>();
		int migrated = 0;
		if (migrate) {
			for (var entry : findReferences().entrySet()) {
				String source = entry.getKey();
				if (!(provider == ImageStorageProvider.LOCAL ? isCloudinary(source) : isLocal(source))) continue;
				try {
					migrateOne(source, entry.getValue(), provider);
					migrated++;
				} catch (RuntimeException e) {
					log.error("Failed to migrate stored file {}", source, e);
					problems.add("Nepodařilo se přenést: " + source);
				}
			}
			cleanPending(problems);
		}
		return status(migrated, problems);
	}

	private void migrateOne(String source, Reference reference, ImageStorageProvider destination) {
		var mapping = cleanup.findFirstBySourceUrl(source);
		if (mapping.isPresent()) {
			String resolved = resolver.resolve(source);
			transactions.executeWithoutResult(_ -> replaceReferences(source, resolved));
			return;
		}
		StoredFileData file = isLocal(source) ? readLocal(source) : cloud.download(source);
		String uploaded = images.uploadTo(file, "expense-tracker/" + reference.folder(), reference.imagesOnly(), destination);
		try {
			transactions.executeWithoutResult(_ -> {
				int updated = replaceReferences(source, uploaded);
				if (updated == 0) throw new IllegalStateException("File reference disappeared during migration");
				cleanup.save(new PendingImageCleanup(source, uploaded));
			});
		} catch (RuntimeException e) {
			// A failed commit can have an unknown outcome; never delete the destination in that case.
			log.error("Migration commit failed; destination was kept for recovery: {}", uploaded, e);
			throw e;
		}
	}

	private void cleanPending(List<String> problems) {
		for (var item : cleanup.findByCleanedFalse()) {
			try {
				if (stillReferenced(item.getSourceUrl())) {
					problems.add("Původní soubor se stále používá, nebyl smazán: " + item.getSourceUrl());
				} else if (deleteSource(item.getSourceUrl())) {
					item.setCleaned(true);
					cleanup.saveAndFlush(item);
				} else {
					problems.add("Nepodařilo se smazat původní soubor: " + item.getSourceUrl());
				}
			} catch (RuntimeException e) {
				log.warn("Failed to remove migrated source file {}", item.getSourceUrl(), e);
				problems.add("Nepodařilo se smazat původní soubor: " + item.getSourceUrl());
			}
		}
	}

	public boolean stillReferenced(String source) {
		for (String reference : findReferences().keySet()) {
			if (source.equals(reference)) return true;
			if (isCloudinary(source) && isCloudinary(reference)) {
				try {
					if (cloud.sameFile(source, reference)) return true;
				} catch (RuntimeException e) {
					log.warn("Cannot rule out another Cloudinary reference to {}; retaining original", source, e);
					return true;
				}
			}
		}
		return false;
	}

	private boolean deleteSource(String url) {
		if (isLocal(url)) return local.deleteByUrl(url);
		if (isCloudinary(url)) return cloud.delete(url);
		throw new IllegalArgumentException("Unsupported source URL");
	}

	private StoredFileData readLocal(String url) {
		String[] parts = url.substring("/images/".length()).split("/", -1);
		if (parts.length != 3) throw new IllegalArgumentException("Invalid local URL");
		var file = local.read(parts[0], parts[1], parts[2]);
		try (var stream = file.resource().getInputStream()) {
			byte[] data = stream.readNBytes(MAX_BYTES + 1);
			if (data.length > MAX_BYTES) throw new IllegalStateException("File exceeds 10 MB");
			return new StoredFileData(file.contentType(), data);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read local file", e);
		}
	}

	private Map<String, Reference> findReferences() {
		return transactions.execute(_ -> {
			Map<String, Reference> result = new LinkedHashMap<>();
			for (Reference reference : REFERENCES) {
				String query = "select distinct e." + reference.field() + " from " + reference.entity()
						+ " e where e." + reference.field() + " is not null";
				for (String url : entityManager.createQuery(query, String.class).getResultList()) {
					result.putIfAbsent(url, reference);
				}
			}
			return result;
		});
	}

	private int replaceReferences(String source, String destination) {
		int updated = 0;
		for (Reference reference : REFERENCES) {
			String query = "update " + reference.entity() + " e set e." + reference.field()
					+ " = :destination where e." + reference.field() + " = :source";
			updated += entityManager.createQuery(query).setParameter("destination", destination)
					.setParameter("source", source).executeUpdate();
		}
		return updated;
	}

	private boolean isLocal(String url) { return url.startsWith("/images/"); }
	private boolean isCloudinary(String url) {
		return url.startsWith("https://res.cloudinary.com/") || url.startsWith("http://res.cloudinary.com/");
	}

	private record Reference(String entity, String field, String folder, boolean imagesOnly) {}
}
