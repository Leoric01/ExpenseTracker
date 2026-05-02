package org.leoric.expensetracker.recurring.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.mapstruct.RecurringBudgetMapper;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringBudgetServiceImplTest {

	@Mock
	private RecurringBudgetTemplateRepository templateRepository;
	@Mock
	private BudgetPlanRepository budgetPlanRepository;
	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private RecurringBudgetMapper mapper;

	@InjectMocks
	private RecurringBudgetServiceImpl service;

	private User user;
	private UUID trackerId;
	private UUID templateId;
	private UUID categoryId;
	private ExpenseTracker tracker;
	private Category category;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		templateId = UUID.randomUUID();
		categoryId = UUID.randomUUID();

		user = User.builder()
				.id(UUID.randomUUID())
				.email("test@example.com")
				.build();

		tracker = ExpenseTracker.builder()
				.id(trackerId)
				.name("Tracker")
				.build();

		category = Category.builder()
				.id(categoryId)
				.name("Culture")
				.expenseTracker(tracker)
				.active(true)
				.build();
	}

	@Test
	void recurringBudgetUpdate_shouldPatchLinkedPlansAndRecomputeNextRunDate() {
		RecurringBudgetTemplate template = RecurringBudgetTemplate.builder()
				.id(templateId)
				.expenseTracker(tracker)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.QUARTERLY)
				.intervalValue(1)
				.startDate(LocalDate.of(2026, 4, 1))
				.nextRunDate(LocalDate.of(2026, 7, 1))
				.active(true)
				.build();

		BudgetPlan first = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.recurringBudgetTemplate(template)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.QUARTERLY)
				.validFrom(LocalDate.of(2026, 4, 1))
				.validTo(LocalDate.of(2026, 6, 30))
				.active(true)
				.build();

		BudgetPlan second = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.recurringBudgetTemplate(template)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.QUARTERLY)
				.validFrom(LocalDate.of(2026, 7, 1))
				.validTo(LocalDate.of(2026, 9, 30))
				.active(true)
				.build();

		UpdateRecurringBudgetRequestDto request = new UpdateRecurringBudgetRequestDto(
				"Kino / Divadlo",
				100000L,
				"CZK",
				PeriodType.YEARLY,
				1,
				LocalDate.of(2026, 1, 1),
				null,
				categoryId
		);

		when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
		when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
		when(templateRepository.save(any(RecurringBudgetTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
		when(budgetPlanRepository.findByRecurringBudgetTemplateIdAndActiveTrueOrderByValidFromAsc(templateId))
				.thenReturn(List.of(first, second));
		when(budgetPlanRepository.save(any(BudgetPlan.class))).thenAnswer(inv -> inv.getArgument(0));

		doAnswer(inv -> {
			UpdateRecurringBudgetRequestDto dto = inv.getArgument(0);
			RecurringBudgetTemplate entity = inv.getArgument(1);
			entity.setName(dto.name());
			entity.setAmount(dto.amount());
			entity.setCurrencyCode(dto.currencyCode());
			entity.setPeriodType(dto.periodType());
			entity.setIntervalValue(dto.intervalValue());
			entity.setStartDate(dto.startDate());
			entity.setEndDate(dto.endDate());
			return null;
		}).when(mapper).updateFromDto(any(UpdateRecurringBudgetRequestDto.class), any(RecurringBudgetTemplate.class));

		doAnswer(inv -> {
			RecurringBudgetTemplate src = inv.getArgument(0);
			BudgetPlan target = inv.getArgument(1);
			target.setName(src.getName());
			target.setAmount(src.getAmount());
			target.setCurrencyCode(src.getCurrencyCode());
			target.setPeriodType(src.getPeriodType());
			target.setCategory(src.getCategory());
			return null;
		}).when(mapper).updateBudgetPlanFromTemplate(any(RecurringBudgetTemplate.class), any(BudgetPlan.class));

		when(mapper.toResponse(any(RecurringBudgetTemplate.class))).thenAnswer(inv -> {
			RecurringBudgetTemplate t = inv.getArgument(0);
			return new RecurringBudgetResponseDto(
					t.getId(),
					t.getName(),
					t.getAmount(),
					t.getCurrencyCode(),
					t.getPeriodType(),
					t.getIntervalValue(),
					t.getCategory() != null ? t.getCategory().getId() : null,
					t.getCategory() != null ? t.getCategory().getName() : null,
					t.getStartDate(),
					t.getEndDate(),
					t.getNextRunDate(),
					t.isActive(),
					null,
					null
			);
		});

		RecurringBudgetResponseDto response = service.recurringBudgetUpdate(user, trackerId, templateId, request);

		assertThat(response.name()).isEqualTo("Kino / Divadlo");
		assertThat(first.getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(first.getValidTo()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(second.getValidFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(second.getValidTo()).isEqualTo(LocalDate.of(2027, 12, 31));
		assertThat(first.getAmount()).isEqualTo(100000);
		assertThat(second.getAmount()).isEqualTo(100000);
		assertThat(first.getPeriodType()).isEqualTo(PeriodType.YEARLY);
		assertThat(second.getPeriodType()).isEqualTo(PeriodType.YEARLY);
		assertThat(first.getId()).isNotEqualTo(templateId);
		assertThat(second.getId()).isNotEqualTo(templateId);
		assertThat(template.getNextRunDate()).isEqualTo(LocalDate.of(2028, 1, 1));
		assertThat(template.isActive()).isTrue();

		verify(budgetPlanRepository, times(2)).save(any(BudgetPlan.class));
		verify(templateRepository, atLeast(2)).save(any(RecurringBudgetTemplate.class));
	}

	@Test
	void recurringBudgetUpdate_shouldDeactivateTemplateWhenNextRunExceedsEndDate() {
		RecurringBudgetTemplate template = RecurringBudgetTemplate.builder()
				.id(templateId)
				.expenseTracker(tracker)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.YEARLY)
				.intervalValue(1)
				.startDate(LocalDate.of(2026, 1, 1))
				.nextRunDate(LocalDate.of(2027, 1, 1))
				.active(true)
				.build();

		BudgetPlan first = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.recurringBudgetTemplate(template)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.YEARLY)
				.validFrom(LocalDate.of(2026, 1, 1))
				.validTo(LocalDate.of(2026, 12, 31))
				.active(true)
				.build();

		BudgetPlan second = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.recurringBudgetTemplate(template)
				.category(category)
				.name("Old")
				.amount(50000)
				.currencyCode("CZK")
				.periodType(PeriodType.YEARLY)
				.validFrom(LocalDate.of(2027, 1, 1))
				.validTo(LocalDate.of(2027, 12, 31))
				.active(true)
				.build();

		UpdateRecurringBudgetRequestDto request = new UpdateRecurringBudgetRequestDto(
				"Kino / Divadlo",
				100000L,
				"CZK",
				PeriodType.YEARLY,
				1,
				LocalDate.of(2026, 1, 1),
				LocalDate.of(2027, 6, 30),
				categoryId
		);

		when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
		when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
		when(templateRepository.save(any(RecurringBudgetTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
		when(budgetPlanRepository.findByRecurringBudgetTemplateIdAndActiveTrueOrderByValidFromAsc(templateId))
				.thenReturn(List.of(first, second));
		when(budgetPlanRepository.save(any(BudgetPlan.class))).thenAnswer(inv -> inv.getArgument(0));

		doAnswer(inv -> {
			UpdateRecurringBudgetRequestDto dto = inv.getArgument(0);
			RecurringBudgetTemplate entity = inv.getArgument(1);
			entity.setName(dto.name());
			entity.setAmount(dto.amount());
			entity.setCurrencyCode(dto.currencyCode());
			entity.setPeriodType(dto.periodType());
			entity.setIntervalValue(dto.intervalValue());
			entity.setStartDate(dto.startDate());
			entity.setEndDate(dto.endDate());
			return null;
		}).when(mapper).updateFromDto(any(UpdateRecurringBudgetRequestDto.class), any(RecurringBudgetTemplate.class));

		doAnswer(inv -> {
			RecurringBudgetTemplate src = inv.getArgument(0);
			BudgetPlan target = inv.getArgument(1);
			target.setName(src.getName());
			target.setAmount(src.getAmount());
			target.setCurrencyCode(src.getCurrencyCode());
			target.setPeriodType(src.getPeriodType());
			target.setCategory(src.getCategory());
			return null;
		}).when(mapper).updateBudgetPlanFromTemplate(any(RecurringBudgetTemplate.class), any(BudgetPlan.class));

		when(mapper.toResponse(any(RecurringBudgetTemplate.class))).thenAnswer(inv -> {
			RecurringBudgetTemplate t = inv.getArgument(0);
			return new RecurringBudgetResponseDto(
					t.getId(),
					t.getName(),
					t.getAmount(),
					t.getCurrencyCode(),
					t.getPeriodType(),
					t.getIntervalValue(),
					t.getCategory() != null ? t.getCategory().getId() : null,
					t.getCategory() != null ? t.getCategory().getName() : null,
					t.getStartDate(),
					t.getEndDate(),
					t.getNextRunDate(),
					t.isActive(),
					null,
					null
			);
		});

		RecurringBudgetResponseDto response = service.recurringBudgetUpdate(user, trackerId, templateId, request);

		assertThat(response.active()).isFalse();
		assertThat(template.isActive()).isFalse();
		assertThat(template.getNextRunDate()).isEqualTo(LocalDate.of(2028, 1, 1));
	}
}