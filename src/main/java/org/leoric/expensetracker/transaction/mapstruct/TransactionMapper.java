package org.leoric.expensetracker.transaction.mapstruct;

import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.TransactionAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

	@Mapping(source = "holding.id", target = "holdingId")
	@Mapping(source = "holding.account.name", target = "holdingName")
	@Mapping(source = "sourceHolding.id", target = "sourceHoldingId")
	@Mapping(source = "sourceHolding.account.name", target = "sourceHoldingName")
	@Mapping(source = "targetHolding.id", target = "targetHoldingId")
	@Mapping(source = "targetHolding.account.name", target = "targetHoldingName")
	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	@Mapping(target = "rootCategoryId", ignore = true)
	@Mapping(target = "rootCategoryName", ignore = true)
	TransactionResponseDto toResponse(Transaction entity);

	TransactionAttachmentResponseDto toAttachmentResponse(TransactionAttachment entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}