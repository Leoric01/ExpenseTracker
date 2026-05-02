package org.leoric.expensetracker.category.dto;

public record PageMetadata(
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}