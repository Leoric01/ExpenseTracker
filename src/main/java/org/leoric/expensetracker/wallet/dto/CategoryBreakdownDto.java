package org.leoric.expensetracker.wallet.dto;

import java.util.UUID;

public record CategoryBreakdownDto(
		UUID categoryId,
		String categoryName,
		long total
) {
}