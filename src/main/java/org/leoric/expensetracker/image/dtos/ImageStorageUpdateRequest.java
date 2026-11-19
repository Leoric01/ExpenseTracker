package org.leoric.expensetracker.image.dtos;

import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.image.models.ImageStorageProvider;

public record ImageStorageUpdateRequest(@NotNull ImageStorageProvider provider, @NotNull Boolean migrate) {}
