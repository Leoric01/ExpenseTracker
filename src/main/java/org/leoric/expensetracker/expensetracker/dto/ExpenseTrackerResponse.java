package org.leoric.expensetracker.expensetracker.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseTrackerResponse(
		UUID id,
		String name,
		String description,
		String defaultCurrencyCode,
		boolean active,
		String ownerFullName,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}