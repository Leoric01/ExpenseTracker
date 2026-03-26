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

	@Mapping(source = "wallet.id", target = "walletId")
	@Mapping(source = "wallet.name", target = "walletName")
	@Mapping(source = "sourceWallet.id", target = "sourceWalletId")
	@Mapping(source = "sourceWallet.name", target = "sourceWalletName")
	@Mapping(source = "targetWallet.id", target = "targetWalletId")
	@Mapping(source = "targetWallet.name", target = "targetWalletName")
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