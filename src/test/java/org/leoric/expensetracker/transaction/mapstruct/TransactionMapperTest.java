package org.leoric.expensetracker.transaction.mapstruct;

import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.TransactionAttachment;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

	private final TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);

	@Test
	void toResponse_shouldMapNestedFieldsAndCurrencyAlias() {
		Holding holding = Holding.builder()
				.id(UUID.randomUUID())
				.account(Account.builder().name("Cash account").build())
				.asset(Asset.builder().code("CZK").scale(2).build())
				.build();
		Holding sourceHolding = Holding.builder()
				.id(UUID.randomUUID())
				.account(Account.builder().name("BTC wallet").build())
				.asset(Asset.builder().code("BTC").scale(8).build())
				.build();
		Holding targetHolding = Holding.builder()
				.id(UUID.randomUUID())
				.account(Account.builder().name("Exchange").build())
				.asset(Asset.builder().code("EUR").scale(2).build())
				.build();
		Category category = Category.builder().id(UUID.randomUUID()).name("Food").build();
		Transaction tx = Transaction.builder()
				.id(UUID.randomUUID())
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.holding(holding)
				.sourceHolding(sourceHolding)
				.targetHolding(targetHolding)
				.category(category)
				.amount(123L)
				.currencyCode("CZK")
				.transactionDate(Instant.parse("2026-05-01T10:00:00Z"))
				.build();

		TransactionResponseDto response = mapper.toResponse(tx);

		assertThat(response.holdingId()).isEqualTo(holding.getId());
		assertThat(response.holdingName()).isEqualTo("Cash account");
		assertThat(response.sourceHoldingId()).isEqualTo(sourceHolding.getId());
		assertThat(response.sourceHoldingName()).isEqualTo("BTC wallet");
		assertThat(response.sourceHoldingAssetCode()).isEqualTo("BTC");
		assertThat(response.sourceHoldingAssetScale()).isEqualTo(8);
		assertThat(response.targetHoldingId()).isEqualTo(targetHolding.getId());
		assertThat(response.targetHoldingName()).isEqualTo("Exchange");
		assertThat(response.targetHoldingAssetCode()).isEqualTo("EUR");
		assertThat(response.targetHoldingAssetScale()).isEqualTo(2);
		assertThat(response.categoryId()).isEqualTo(category.getId());
		assertThat(response.categoryName()).isEqualTo("Food");
		assertThat(response.assetCode()).isEqualTo("CZK");
		assertThat(response.assetScale()).isNull();
	}

	@Test
	void toAttachmentResponse_shouldMapAttachment() {
		TransactionAttachment attachment = TransactionAttachment.builder()
				.id(UUID.randomUUID())
				.fileName("receipt.png")
				.fileUrl("https://cdn/receipt.png")
				.contentType("image/png")
				.fileSize(321L)
				.createdDate(Instant.parse("2026-05-01T09:00:00Z"))
				.build();

		TransactionAttachmentResponseDto response = mapper.toAttachmentResponse(attachment);

		assertThat(response.id()).isEqualTo(attachment.getId());
		assertThat(response.fileName()).isEqualTo("receipt.png");
		assertThat(response.fileUrl()).isEqualTo("https://cdn/receipt.png");
		assertThat(response.contentType()).isEqualTo("image/png");
		assertThat(response.fileSize()).isEqualTo(321L);
		assertThat(response.createdDate()).isEqualTo(OffsetDateTime.parse("2026-05-01T09:00:00Z"));
	}

	@Test
	void map_shouldConvertInstantToUtcOffsetDateTime() {
		OffsetDateTime mapped = mapper.map(Instant.parse("2026-05-01T12:34:56Z"));

		assertThat(mapped).isEqualTo(OffsetDateTime.parse("2026-05-01T12:34:56Z"));
		assertThat(mapper.map(null)).isNull();
	}
}