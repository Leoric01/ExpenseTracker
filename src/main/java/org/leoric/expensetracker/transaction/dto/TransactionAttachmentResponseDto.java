package org.leoric.expensetracker.transaction.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionAttachmentResponseDto(
		UUID id,
		String fileName,
		String fileUrl,
		String contentType,
		long fileSize,
		OffsetDateTime createdDate
) {
}