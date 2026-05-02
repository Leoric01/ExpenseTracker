package org.leoric.expensetracker.recurring.mapstruct;

import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.recurring.dto.RecurringTransactionResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringTransactionMapperTest {

	private final RecurringTransactionMapper mapper = Mappers.getMapper(RecurringTransactionMapper.class);

	@Test
	void toResponse_shouldMapNestedFields() {
		Holding holding = Holding.builder()
				.id(UUID.randomUUID())
				.account(Account.builder().name("Main wallet").build())
				.asset(Asset.builder().code("CZK").scale(2).build())
				.build();
		Category category = Category.builder().id(UUID.randomUUID()).name("Bills").build();
		RecurringTransactionTemplate entity = RecurringTransactionTemplate.builder()
				.id(UUID.randomUUID())
				.transactionType(TransactionType.EXPENSE)
				.holding(holding)
				.category(category)
				.amount(1500L)
				.currencyCode("CZK")
				.description("Internet")
				.periodType(PeriodType.MONTHLY)
				.intervalValue(1)
				.startDate(LocalDate.of(2026, 5, 1))
				.nextRunDate(LocalDate.of(2026, 6, 1))
				.active(true)
				.createdDate(Instant.parse("2026-05-01T10:00:00Z"))
				.lastModifiedDate(Instant.parse("2026-05-02T10:00:00Z"))
				.build();

		RecurringTransactionResponseDto response = mapper.toResponse(entity);

		assertThat(response.id()).isEqualTo(entity.getId());
		assertThat(response.holdingId()).isEqualTo(holding.getId());
		assertThat(response.holdingName()).isEqualTo("Main wallet");
		assertThat(response.assetCode()).isEqualTo("CZK");
		assertThat(response.assetScale()).isEqualTo(holding.getAsset().getScale());
		assertThat(response.categoryId()).isEqualTo(category.getId());
		assertThat(response.categoryName()).isEqualTo("Bills");
		assertThat(response.createdDate()).isEqualTo(OffsetDateTime.parse("2026-05-01T10:00:00Z"));
	}

	@Test
	void updateFromDto_shouldIgnoreNullsAndKeepAssociations() {
		Holding originalHolding = Holding.builder().id(UUID.randomUUID()).account(Account.builder().name("Old").build()).build();
		Category originalCategory = Category.builder().id(UUID.randomUUID()).name("Old category").build();
		RecurringTransactionTemplate entity = RecurringTransactionTemplate.builder()
				.holding(originalHolding)
				.category(originalCategory)
				.amount(100L)
				.currencyCode("CZK")
				.description("Old")
				.note("Old note")
				.periodType(PeriodType.MONTHLY)
				.intervalValue(1)
				.startDate(LocalDate.of(2026, 1, 1))
				.endDate(LocalDate.of(2026, 12, 31))
				.build();
		UpdateRecurringTransactionRequestDto dto = new UpdateRecurringTransactionRequestDto(
				UUID.randomUUID(),
				UUID.randomUUID(),
				500L,
				null,
				"New description",
				null,
				PeriodType.YEARLY,
				2,
				LocalDate.of(2026, 2, 1),
				null
		);

		mapper.updateFromDto(dto, entity);

		assertThat(entity.getHolding()).isSameAs(originalHolding);
		assertThat(entity.getCategory()).isSameAs(originalCategory);
		assertThat(entity.getAmount()).isEqualTo(500L);
		assertThat(entity.getCurrencyCode()).isEqualTo("CZK");
		assertThat(entity.getDescription()).isEqualTo("New description");
		assertThat(entity.getNote()).isEqualTo("Old note");
		assertThat(entity.getPeriodType()).isEqualTo(PeriodType.YEARLY);
		assertThat(entity.getIntervalValue()).isEqualTo(2);
		assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
		assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void map_shouldConvertInstantToUtcOffsetDateTime() {
		OffsetDateTime mapped = mapper.map(Instant.parse("2026-05-01T12:34:56Z"));

		assertThat(mapped).isEqualTo(OffsetDateTime.parse("2026-05-01T12:34:56Z"));
		assertThat(mapper.map(null)).isNull();
	}
}