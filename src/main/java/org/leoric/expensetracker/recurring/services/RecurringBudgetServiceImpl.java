package org.leoric.expensetracker.recurring.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.mapstruct.RecurringBudgetMapper;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.leoric.expensetracker.recurring.services.interfaces.RecurringBudgetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringBudgetServiceImpl implements RecurringBudgetService {

	private final RecurringBudgetTemplateRepository templateRepository;
	private final BudgetPlanRepository budgetPlanRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final CategoryRepository categoryRepository;
	private final RecurringBudgetMapper mapper;

	@Override
	@Transactional
	public RecurringBudgetResponseDto recurringBudgetCreate(User currentUser, UUID trackerId, CreateRecurringBudgetRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		Category category = null;
		if (request.categoryId() != null) {
			category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
		}

		RecurringBudgetTemplate template = RecurringBudgetTemplate.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.amount(request.amount())
				.currencyCode(request.currencyCode().toUpperCase())
				.periodType(request.periodType())
				.intervalValue(request.intervalValue() != null ? request.intervalValue() : 1)
				.startDate(request.startDate())
				.endDate(request.endDate())
				.nextRunDate(request.startDate())
				.category(category)
				.build();

		template = templateRepository.save(template);
		log.info("User {} created recurring budget template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getName(), tracker.getName());

		// Immediately generate budget plans for periods that are already due
		generateDueBudgetPlans(template);

		return mapper.toResponse(template);
	}

	@Override
	@Transactional(readOnly = true)
	public RecurringBudgetResponseDto recurringBudgetFindById(User currentUser, UUID trackerId, UUID templateId) {
		RecurringBudgetTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);
		return mapper.toResponse(template);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<RecurringBudgetResponseDto> recurringBudgetFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return templateRepository.findByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(mapper::toResponse);
		}
		return templateRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(mapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<RecurringBudgetResponseDto> recurringBudgetFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return templateRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(mapper::toResponse);
		}
		return templateRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(mapper::toResponse);
	}

	@Override
	@Transactional
	public RecurringBudgetResponseDto recurringBudgetUpdate(User currentUser, UUID trackerId, UUID templateId, UpdateRecurringBudgetRequestDto request) {
		RecurringBudgetTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);

		Category newCategory = null;
		if (request.categoryId() != null) {
			newCategory = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(newCategory, trackerId);
			template.setCategory(newCategory);
		}

		mapper.updateFromDto(request, template);

		if (request.currencyCode() != null) {
			template.setCurrencyCode(request.currencyCode().toUpperCase());
		}

		template = templateRepository.save(template);
		log.info("User {} updated recurring budget template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getName(), template.getExpenseTracker().getName());

		// Update currently active budget plan(s) generated from this template
		updateCurrentBudgetPlans(template, newCategory);

		return mapper.toResponse(template);
	}

	@Override
	@Transactional
	public void recurringBudgetDeactivate(User currentUser, UUID trackerId, UUID templateId) {
		RecurringBudgetTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);

		if (!template.isActive()) {
			throw new OperationNotPermittedException("Recurring budget template is already deactivated");
		}

		template.setActive(false);
		templateRepository.save(template);
		log.info("User {} deactivated recurring budget template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getName(), template.getExpenseTracker().getName());
	}

	// ── Helpers ──

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private RecurringBudgetTemplate getTemplateOrThrow(UUID templateId) {
		return templateRepository.findById(templateId)
				.orElseThrow(() -> new EntityNotFoundException("Recurring budget template not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertTemplateBelongsToTracker(RecurringBudgetTemplate template, UUID trackerId) {
		if (!template.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Recurring budget template not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}

	private void updateCurrentBudgetPlans(RecurringBudgetTemplate template, Category newCategory) {
		LocalDate today = LocalDate.now();
		List<BudgetPlan> currentPlans = budgetPlanRepository.findCurrentActiveByRecurringTemplateId(template.getId(), today);

		for (BudgetPlan plan : currentPlans) {
			plan.setName(template.getName());
			plan.setAmount(template.getAmount());
			plan.setCurrencyCode(template.getCurrencyCode());
			plan.setPeriodType(template.getPeriodType());
			if (newCategory != null) {
				plan.setCategory(newCategory);
			}
			budgetPlanRepository.save(plan);
			log.info("Updated current budget plan '{}' (valid {} — {}) to match recurring template '{}'",
					plan.getName(), plan.getValidFrom(), plan.getValidTo(), template.getId());
		}
	}

	private void generateDueBudgetPlans(RecurringBudgetTemplate template) {
		LocalDate today = LocalDate.now();

		while (template.getNextRunDate() != null && !template.getNextRunDate().isAfter(today)) {
			// Respect endDate
			if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
				template.setActive(false);
				templateRepository.save(template);
				log.info("Deactivated expired recurring budget template '{}' during initial catch-up (endDate {})",
						template.getId(), template.getEndDate());
				return;
			}

			LocalDate planValidFrom = template.getNextRunDate();
			LocalDate planValidTo = computeNextRunDate(planValidFrom, template.getPeriodType(), template.getIntervalValue()).minusDays(1);

			BudgetPlan plan = BudgetPlan.builder()
					.expenseTracker(template.getExpenseTracker())
					.recurringBudgetTemplate(template)
					.category(template.getCategory())
					.name(template.getName())
					.amount(template.getAmount())
					.currencyCode(template.getCurrencyCode())
					.periodType(template.getPeriodType())
					.validFrom(planValidFrom)
					.validTo(planValidTo)
					.build();

			budgetPlanRepository.save(plan);
			log.info("Generated initial budget plan '{}' (valid {} — {}) from recurring template '{}'",
					plan.getName(), planValidFrom, planValidTo, template.getId());

			LocalDate nextRun = computeNextRunDate(template.getNextRunDate(), template.getPeriodType(), template.getIntervalValue());

			if (template.getEndDate() != null && nextRun.isAfter(template.getEndDate())) {
				template.setActive(false);
				template.setNextRunDate(nextRun);
				templateRepository.save(template);
				log.info("Deactivated recurring budget template '{}' after catch-up (next run {} past endDate {})",
						template.getId(), nextRun, template.getEndDate());
				return;
			}

			template.setNextRunDate(nextRun);
			templateRepository.save(template);
		}
	}

	private LocalDate computeNextRunDate(LocalDate current, PeriodType periodType, int interval) {
		return switch (periodType) {
			case DAILY -> current.plusDays(interval);
			case WEEKLY -> current.plusWeeks(interval);
			case MONTHLY -> current.plusMonths(interval);
			case QUARTERLY -> current.plusMonths(3L * interval);
			case YEARLY -> current.plusYears(interval);
		};
	}
}