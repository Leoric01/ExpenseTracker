package org.leoric.expensetracker.category.dto;

import java.util.List;

public record CategoryActivePageResponse(
		List<CategoryActiveRowResponse> categories,
		PageMetadata page
) {
}