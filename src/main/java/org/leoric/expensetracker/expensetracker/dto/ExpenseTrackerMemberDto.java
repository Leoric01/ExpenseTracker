package org.leoric.expensetracker.expensetracker.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record ExpenseTrackerMemberDto(
		UUID userId,
		String fullName,
		String email,
		String role,
		List<String> roles,
		OffsetDateTime memberSince
) {
}