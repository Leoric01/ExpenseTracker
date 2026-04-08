package org.leoric.expensetracker.institution.dto;

import org.leoric.expensetracker.institution.models.constants.InstitutionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InstitutionResponseDto(
		UUID id,
		String name,
		InstitutionType institutionType,
		String description,
		String iconUrl,
		String iconColor,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}