package org.leoric.expensetracker.expensetracker.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExpenseTrackerResponseDto(
		UUID id,
		String name,
		String description,
		String defaultCurrencyCode,
		boolean active,
		String ownerFullName,
		List<ExpenseTrackerMemberDto> members,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}