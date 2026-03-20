package org.leoric.expensetracker.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponseFullDto(
		UUID id,
		String email,
		String firstName,
		String lastName,
		List<String> roles,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}