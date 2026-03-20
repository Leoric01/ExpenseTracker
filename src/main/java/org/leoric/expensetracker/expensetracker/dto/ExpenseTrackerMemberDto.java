package org.leoric.expensetracker.expensetracker.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseTrackerMemberDto(
		UUID userId,
		String fullName,
		String email,
		String role,
		OffsetDateTime memberSince
) {
}