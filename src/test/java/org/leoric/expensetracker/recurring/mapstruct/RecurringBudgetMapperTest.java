package org.leoric.expensetracker.recurring.mapstruct;

import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringBudgetMapperTest {

	private final RecurringBudgetMapper mapper = Mappers.getMapper(RecurringBudgetMapper.class);

	@Test
	void toEntity_shouldUppercaseCurrencyAndDefaultIntervalAndMapNextRunDate() {
		CreateRecurringBudgetRequestDto dto = new CreateRecurringBudgetRequestDto(
				"Rent",
				20_000L,
				"czk",
				PeriodType.MONTHLY,
				null,
				LocalDate.of(2026, 5, 1),
				null,
				null
		);

		RecurringBudgetTemplate entity = mapper.toEntity(dto);

		assertThat(entity.getCurrencyCode()).isEqualTo("CZK");
		assertThat(entity.getIntervalValue()).isEqualTo(1);
		assertThat(entity.getNextRunDate()).isEqualTo(LocalDate.of(2026, 5, 1));
		assertThat(entity.getAmount()).isEqualTo(20_000L);
	}

	@Test
	void toResponse_shouldMapCategoryAndDates() {
		Category category = Category.builder().id(UUID.randomUUID()).name("Housing").build();
		RecurringBudgetTemplate entity = RecurringBudgetTemplate.builder()
				.id(UUID.randomUUID())
				.name("Rent")
				.amount(20_000L)
				.currencyCode("CZK")
				.periodType(PeriodType.MONTHLY)
				.intervalValue(1)
				.category(category)
				.startDate(LocalDate.of(2026, 5, 1))
				.nextRunDate(LocalDate.of(2026, 6, 1))
				.active(true)
				.createdDate(Instant.parse("2026-05-01T10:00:00Z"))
				.lastModifiedDate(Instant.parse("2026-05-02T10:00:00Z"))
				.build();

		RecurringBudgetResponseDto response = mapper.toResponse(entity);

		assertThat(response.id()).isEqualTo(entity.getId());
		assertThat(response.assetCode()).isEqualTo("CZK");
		assertThat(response.assetScale()).isNull();
		assertThat(response.categoryId()).isEqualTo(category.getId());
		assertThat(response.categoryName()).isEqualTo("Housing");
		assertThat(response.createdDate()).isEqualTo(OffsetDateTime.parse("2026-05-01T10:00:00Z"));
	}

	@Test
	void toBudgetPlan_shouldCopyTemplateAndProvidedValidity() {
		RecurringBudgetTemplate template = RecurringBudgetTemplate.builder()
				.id(UUID.randomUUID())
				.name("Rent")
				.amount(20_000L)
				.currencyCode("CZK")
				.periodType(PeriodType.MONTHLY)
				.category(Category.builder().id(UUID.randomUUID()).name("Housing").build())
				.build();

		BudgetPlan plan = mapper.toBudgetPlan(template, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

		assertThat(plan.getRecurringBudgetTemplate()).isSameAs(template);
		assertThat(plan.getName()).isEqualTo("Rent");
		assertThat(plan.getAmount()).isEqualTo(20_000L);
		assertThat(plan.getCurrencyCode()).isEqualTo("CZK");
		assertThat(plan.getPeriodType()).isEqualTo(PeriodType.MONTHLY);
		assertThat(plan.getValidFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
		assertThat(plan.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
	}

	@Test
	void updateFromDto_shouldIgnoreNullsAndUppercaseCurrency() {
		RecurringBudgetTemplate entity = RecurringBudgetTemplate.builder()
				.name("Old")
				.amount(10_000L)
				.currencyCode("CZK")
				.periodType(PeriodType.MONTHLY)
				.intervalValue(1)
				.startDate(LocalDate.of(2026, 1, 1))
				.build();
		UpdateRecurringBudgetRequestDto dto = new UpdateRecurringBudgetRequestDto(
				"New",
				null,
				"usd",
				PeriodType.YEARLY,
				2,
				LocalDate.of(2026, 2, 1),
				null,
				null
		);

		mapper.updateFromDto(dto, entity);

		assertThat(entity.getName()).isEqualTo("New");
		assertThat(entity.getAmount()).isEqualTo(10_000L);
		assertThat(entity.getCurrencyCode()).isEqualTo("USD");
		assertThat(entity.getPeriodType()).isEqualTo(PeriodType.YEARLY);
		assertThat(entity.getIntervalValue()).isEqualTo(2);
		assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
	}

	@Test
	void map_shouldConvertInstantToUtcOffsetDateTime() {
		assertThat(mapper.map(Instant.parse("2026-05-01T12:34:56Z")))
				.isEqualTo(OffsetDateTime.parse("2026-05-01T12:34:56Z"));
		assertThat(mapper.map(null)).isNull();
	}
}