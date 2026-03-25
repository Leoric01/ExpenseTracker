package org.leoric.expensetracker.transaction.dto;

public record PageMetaDto(
		int size,
		int number,
		long totalElements,
		int totalPages
) {
}