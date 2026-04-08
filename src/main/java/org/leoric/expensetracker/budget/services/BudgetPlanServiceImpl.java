package org.leoric.expensetracker.budget.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportResponseDto.BulkBudgetImportFailureDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportResponseDto.BulkBudgetImportSuccessDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.mapstruct.BudgetPlanMapper;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.budget.services.interfaces.BudgetPlanService;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateBudgetPlanNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.leoric.expensetracker.utils.BudgetPlanSpentCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetPlanServiceImpl implements BudgetPlanService {

	private final BudgetPlanSpentCalculator budgetPlanSpentCalculator;
	private final BudgetPlanRepository budgetPlanRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final CategoryRepository categoryRepository;
	private final AssetRepository assetRepository;
	private final RecurringBudgetTemplateRepository recurringBudgetTemplateRepository;
	private final BudgetPlanMapper budgetPlanMapper;

	@Override
	@Transactional
	public BudgetPlanResponseDto budgetPlanCreate(User currentUser, UUID trackerId, CreateBudgetPlanRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (budgetPlanRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new DuplicateBudgetPlanNameException(
					"Budget plan with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		Category category = null;
		if (request.categoryId() != null) {
			category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
		}

		BudgetPlan budgetPlan = BudgetPlan.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.amount(request.amount())
				.currencyCode(request.currencyCode().toUpperCase())
				.periodType(request.periodType())
				.validFrom(request.validFrom())
				.validTo(request.validTo())
				.category(category)
				.build();

		budgetPlan = budgetPlanRepository.save(budgetPlan);
		log.info("User {} created budget plan '{}' in tracker '{}'",
		         currentUser.getEmail(), budgetPlan.getName(), tracker.getName());

		return toResponseWithSpent(budgetPlan);
	}

	@Override
	@Transactional(readOnly = true)
	public BudgetPlanResponseDto budgetPlanFindById(User currentUser, UUID trackerId, UUID budgetPlanId) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);
		return toResponseWithSpent(budgetPlan);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<BudgetPlanResponseDto> budgetPlanFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return budgetPlanRepository.findByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(this::toResponseWithSpent);
		}
		return budgetPlanRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(this::toResponseWithSpent);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<BudgetPlanResponseDto> budgetPlanFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable) {
		LocalDate today = LocalDate.now();
		if (search != null && !search.isBlank()) {
			return budgetPlanRepository.findCurrentActiveByExpenseTrackerIdWithSearch(trackerId, today, search, pageable)
					.map(this::toResponseWithSpent);
		}
		return budgetPlanRepository.findCurrentActiveByExpenseTrackerId(trackerId, today, pageable)
				.map(this::toResponseWithSpent);
	}

	@Override
	@Transactional
	public BudgetPlanResponseDto budgetPlanUpdate(User currentUser, UUID trackerId, UUID budgetPlanId, UpdateBudgetPlanRequestDto request) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);

		if (request.categoryId() != null) {
			Category category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
			budgetPlan.setCategory(category);
		}

		budgetPlanMapper.updateFromDto(request, budgetPlan);

		if (request.currencyCode() != null) {
			budgetPlan.setCurrencyCode(request.currencyCode().toUpperCase());
		}

		budgetPlan = budgetPlanRepository.save(budgetPlan);
		log.info("User {} updated budget plan '{}' in tracker '{}'",
		         currentUser.getEmail(), budgetPlan.getName(), budgetPlan.getExpenseTracker().getName());

		return toResponseWithSpent(budgetPlan);
	}

	@Override
	@Transactional
	public void budgetPlanDeactivate(User currentUser, UUID trackerId, UUID budgetPlanId) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);

		if (!budgetPlan.isActive()) {
			throw new OperationNotPermittedException("Budget plan is already deactivated");
		}

		budgetPlan.setActive(false);
		budgetPlanRepository.save(budgetPlan);
		log.info("User {} deactivated budget plan '{}' in tracker '{}'",
		         currentUser.getEmail(), budgetPlan.getName(), budgetPlan.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public BulkBudgetImportResponseDto bulkImport(User currentUser, UUID trackerId, List<BulkBudgetImportItemDto> items) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		List<BulkBudgetImportSuccessDto> successes = new ArrayList<>();
		List<BulkBudgetImportFailureDto> failures = new ArrayList<>();

		LocalDate today = LocalDate.now();

		for (BulkBudgetImportItemDto item : items) {
			List<String> warnings = new ArrayList<>();

			// 1. Parse PeriodType
			PeriodType periodType;
			try {
				periodType = PeriodType.valueOf(item.period().toUpperCase());
			} catch (IllegalArgumentException e) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Invalid period type '%s'. Valid values: DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY".formatted(item.period())));
				continue;
			}

			// 2. Validate currency against asset table
			String currencyCode = item.currency().toUpperCase();
			if (!assetRepository.existsByCodeIgnoreCase(currencyCode)) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Asset (currency) '%s' not found in the system".formatted(item.currency())));
				continue;
			}

			// 3. Resolve category by name
			Optional<Category> categoryOpt = categoryRepository
					.findFirstByExpenseTrackerIdAndNameIgnoreCaseAndActiveTrue(trackerId, item.budgetPlanName());
			boolean categoryMatched = categoryOpt.isPresent();
			Category category = categoryOpt.orElse(null);

			if (!categoryMatched) {
				warnings.add("Category '%s' not found in tracker".formatted(item.budgetPlanName()));
			}

			// 4. Calculate period-aligned dates
			LocalDate validFrom = computePeriodStart(today, periodType);
			LocalDate validTo = computePeriodEnd(today, periodType);

			// 5. Build unique name for the budget plan (name + period to avoid duplicates)
			String planName = item.budgetPlanName();

			// 6. Create RecurringBudgetTemplate
			RecurringBudgetTemplate template = RecurringBudgetTemplate.builder()
					.expenseTracker(tracker)
					.name(planName)
					.amount(item.amount())
					.currencyCode(currencyCode)
					.periodType(periodType)
					.intervalValue(1)
					.startDate(validFrom)
					.nextRunDate(computeNextPeriodStart(today, periodType))
					.category(category)
					.build();
			template = recurringBudgetTemplateRepository.save(template);
			log.info("Bulk import: created recurring budget template '{}' ({}) in tracker '{}'",
					planName, periodType, tracker.getName());

			// 7. Create BudgetPlan for the current period
			BudgetPlan budgetPlan = BudgetPlan.builder()
					.expenseTracker(tracker)
					.recurringBudgetTemplate(template)
					.name(planName)
					.amount(item.amount())
					.currencyCode(currencyCode)
					.periodType(periodType)
					.validFrom(validFrom)
					.validTo(validTo)
					.category(category)
					.build();
			budgetPlanRepository.save(budgetPlan);
			log.info("Bulk import: created budget plan '{}' ({}, {} — {}) in tracker '{}'",
					planName, periodType, validFrom, validTo, tracker.getName());

			String categoryName = category != null ? category.getName() : null;
			successes.add(new BulkBudgetImportSuccessDto(
					planName, periodType.name(), item.amount(), currencyCode,
					categoryName, categoryMatched, true));

			if (!warnings.isEmpty()) {
				// Also add a partial-failure entry so the caller sees what wasn't matched
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Created but: " + String.join("; ", warnings)));
			}
		}

		log.info("Bulk import complete for tracker '{}': {} succeeded, {} issues",
				tracker.getName(), successes.size(), failures.size());

		return new BulkBudgetImportResponseDto(
				items.size(), successes.size(), failures.size(), successes, failures);
	}

	// ── Period date helpers ──

	private LocalDate computePeriodStart(LocalDate today, PeriodType periodType) {
		return switch (periodType) {
			case DAILY -> today;
			case WEEKLY -> today.with(java.time.DayOfWeek.MONDAY);
			case MONTHLY -> today.withDayOfMonth(1);
			case QUARTERLY -> {
				int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
				yield LocalDate.of(today.getYear(), quarterMonth, 1);
			}
			case YEARLY -> LocalDate.of(today.getYear(), Month.JANUARY, 1);
		};
	}

	private LocalDate computePeriodEnd(LocalDate today, PeriodType periodType) {
		return switch (periodType) {
			case DAILY -> today;
			case WEEKLY -> today.with(java.time.DayOfWeek.MONDAY).plusDays(6);
			case MONTHLY -> today.with(TemporalAdjusters.lastDayOfMonth());
			case QUARTERLY -> {
				int quarterEndMonth = ((today.getMonthValue() - 1) / 3) * 3 + 3;
				yield LocalDate.of(today.getYear(), quarterEndMonth, 1)
						.with(TemporalAdjusters.lastDayOfMonth());
			}
			case YEARLY -> LocalDate.of(today.getYear(), Month.DECEMBER, 31);
		};
	}

	private LocalDate computeNextPeriodStart(LocalDate today, PeriodType periodType) {
		return switch (periodType) {
			case DAILY -> today.plusDays(1);
			case WEEKLY -> today.with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
			case MONTHLY -> today.withDayOfMonth(1).plusMonths(1);
			case QUARTERLY -> {
				int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
				yield LocalDate.of(today.getYear(), quarterMonth, 1).plusMonths(3);
			}
			case YEARLY -> LocalDate.of(today.getYear() + 1, Month.JANUARY, 1);
		};
	}

	// ── Response builder ──

	private BudgetPlanResponseDto toResponseWithSpent(BudgetPlan plan) {
		long alreadySpent = budgetPlanSpentCalculator.computeAlreadySpent(plan);
		return new BudgetPlanResponseDto(
				plan.getId(),
				plan.getName(),
				plan.getAmount(),
				plan.getCurrencyCode(),
				plan.getPeriodType(),
				plan.getCategory() != null ? plan.getCategory().getId() : null,
				plan.getCategory() != null ? plan.getCategory().getName() : null,
				plan.getValidFrom(),
				plan.getValidTo(),
				plan.isActive(),
				alreadySpent,
				plan.getCreatedDate() != null ? plan.getCreatedDate().atOffset(ZoneOffset.UTC) : null,
				plan.getLastModifiedDate() != null ? plan.getLastModifiedDate().atOffset(ZoneOffset.UTC) : null
		);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private BudgetPlan getBudgetPlanOrThrow(UUID budgetPlanId) {
		return budgetPlanRepository.findById(budgetPlanId)
				.orElseThrow(() -> new EntityNotFoundException("Budget plan not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertBudgetPlanBelongsToTracker(BudgetPlan budgetPlan, UUID trackerId) {
		if (!budgetPlan.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Budget plan not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}
}