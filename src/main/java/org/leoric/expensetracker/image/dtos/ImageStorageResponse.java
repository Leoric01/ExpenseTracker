package org.leoric.expensetracker.image.dtos;

import org.leoric.expensetracker.image.models.ImageStorageProvider;

import java.util.List;

public record ImageStorageResponse(ImageStorageProvider provider, boolean cloudinaryConfigured,
		long localFiles, long cloudinaryFiles, long pendingCleanup, int migrated, List<String> problems) {}
