package org.leoric.expensetracker.holding.dto;

import java.util.UUID;

public record CategoryBreakdownDto(
		UUID categoryId,
		String categoryName,
		long total
) {
}