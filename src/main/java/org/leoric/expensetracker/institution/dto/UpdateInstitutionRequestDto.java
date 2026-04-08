package org.leoric.expensetracker.institution.dto;

import org.leoric.expensetracker.institution.models.constants.InstitutionType;

public record UpdateInstitutionRequestDto(
		String name,
		InstitutionType institutionType,
		String description
) {
}