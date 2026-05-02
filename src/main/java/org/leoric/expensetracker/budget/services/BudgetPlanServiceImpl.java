package org.leoric.expensetracker.budget.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetExportIssueDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetExportItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetExportResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportByCategoryIdItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportByCategoryIdRequestDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportRequestDto;
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
import org.leoric.expensetracker.utils.CustomUtilityString;
import org.leoric.expensetracker.utils.BudgetPlanSpentCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

		BudgetPlan budgetPlan = budgetPlanMapper.toEntity(request);
		budgetPlan.setExpenseTracker(tracker);
		budgetPlan.setCategory(category);

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
	@Transactional(readOnly = true)
	public BulkBudgetExportResponseDto budgetPlanExportBulk(User currentUser, UUID trackerId) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		List<BulkBudgetExportItemDto> items = new ArrayList<>();
		List<BulkBudgetExportIssueDto> issues = new ArrayList<>();

		List<RecurringBudgetTemplate> recurringTemplates = recurringBudgetTemplateRepository
				.findByExpenseTrackerIdAndActiveTrue(trackerId);
		for (RecurringBudgetTemplate template : recurringTemplates) {
			addExportEntry(
					template.getName(),
					template.getPeriodType(),
					template.getAmount(),
					template.getCurrencyCode(),
					template.getCategory(),
					template.getIntervalValue(),
					true,
					items,
					issues
			);
		}

		List<BudgetPlan> budgetPlans = budgetPlanRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		for (BudgetPlan plan : budgetPlans) {
			if (plan.getRecurringBudgetTemplate() != null) {
				continue;
			}

			addExportEntry(
					plan.getName(),
					plan.getPeriodType(),
					plan.getAmount(),
					plan.getCurrencyCode(),
					plan.getCategory(),
					null,
					false,
					items,
					issues
			);
		}

		int itemCount = items.size();
		int issueCount = issues.size();

		log.info("User {} exported budgets for tracker '{}': {} items, {} issues",
				currentUser.getEmail(), tracker.getName(), itemCount, issueCount);

		return new BulkBudgetExportResponseDto(
				itemCount + issueCount,
				itemCount,
				issueCount,
				items,
				issues
		);
	}

	@Override
	@Transactional
	public BulkBudgetImportResponseDto budgetPlanImportBulk(User currentUser, UUID trackerId, BulkBudgetImportRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		List<BulkBudgetImportSuccessDto> successes = new ArrayList<>();
		List<BulkBudgetImportFailureDto> failures = new ArrayList<>();
		List<BulkBudgetImportItemDto> items = request.items() != null ? request.items() : List.of();
		int ignoredIssues = request.issues() != null ? request.issues().size() : 0;
		List<Category> activeCategories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		Map<String, List<Category>> categoriesByNormalizedName = activeCategories.stream()
				.collect(Collectors.groupingBy(category -> CustomUtilityString.normalize(category.getName())));
		Map<String, List<Category>> categoriesByRawName = activeCategories.stream()
				.collect(Collectors.groupingBy(category -> category.getName().toLowerCase(Locale.ROOT)));

		LocalDate today = LocalDate.now();

		for (BulkBudgetImportItemDto item : items) {
			PeriodType periodType;
			try {
				periodType = PeriodType.valueOf(item.period().toUpperCase());
			} catch (IllegalArgumentException e) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Invalid period type '%s'. Valid values: %s".formatted(item.period(), getValidPeriodTypeValues())));
				continue;
			}

			String currencyCode = item.currency().toUpperCase();
			if (!assetRepository.existsByCodeIgnoreCase(currencyCode)) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Asset (currency) '%s' not found in the system".formatted(item.currency())));
				continue;
			}

			Category category = resolveCategoryForImport(
					trackerId,
					item.categoryName(),
					categoriesByNormalizedName,
					categoriesByRawName
			);
			if (category == null) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Category '%s' not found or is ambiguous in tracker".formatted(item.categoryName())));
				continue;
			}

			LocalDate validFrom = computePeriodStart(today, periodType);
			LocalDate validTo = computePeriodEnd(today, periodType);
			String planName = item.budgetPlanName();

			RecurringBudgetTemplate template = null;
			boolean recurringCreated = false;
			if (item.recurring()) {
				if (item.intervalValue() == null || item.intervalValue() <= 0) {
					failures.add(new BulkBudgetImportFailureDto(
							item.budgetPlanName(), item.period(), item.amount(), item.currency(),
							"Recurring budget requires positive intervalValue"));
					continue;
				}

				template = RecurringBudgetTemplate.builder()
						.expenseTracker(tracker)
						.name(planName)
						.amount(item.amount())
						.currencyCode(currencyCode)
						.periodType(periodType)
						.intervalValue(item.intervalValue())
						.startDate(validFrom)
						.nextRunDate(computeNextPeriodStart(validFrom, periodType, item.intervalValue()))
						.category(category)
						.build();
				template = recurringBudgetTemplateRepository.save(template);
				recurringCreated = true;
				log.info("Bulk import: created recurring budget template '{}' ({}, interval {}) in tracker '{}'",
						planName, periodType, item.intervalValue(), tracker.getName());
			}

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

			successes.add(new BulkBudgetImportSuccessDto(
					planName,
					periodType.name(),
					item.amount(),
					currencyCode,
					category.getName(),
					true,
					recurringCreated));
		}

		log.info("Bulk import complete for tracker '{}': {} succeeded, {} issues, {} request issues ignored",
				tracker.getName(), successes.size(), failures.size(), ignoredIssues);

		return new BulkBudgetImportResponseDto(
				items.size(), successes.size(), failures.size(), successes, failures);
	}

	@Override
	@Transactional
	public BulkBudgetImportResponseDto budgetPlanImportByCategoryIdBulk(User currentUser, UUID trackerId, BulkBudgetImportByCategoryIdRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		List<BulkBudgetImportSuccessDto> successes = new ArrayList<>();
		List<BulkBudgetImportFailureDto> failures = new ArrayList<>();
		List<BulkBudgetImportByCategoryIdItemDto> items = request.items() != null ? request.items() : List.of();
		int ignoredIssues = request.issues() != null ? request.issues().size() : 0;
		Map<UUID, Category> categoriesById = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)
				.stream()
				.collect(Collectors.toMap(Category::getId, category -> category));

		LocalDate today = LocalDate.now();

		for (BulkBudgetImportByCategoryIdItemDto item : items) {
			PeriodType periodType;
			try {
				periodType = PeriodType.valueOf(item.period().toUpperCase());
			} catch (IllegalArgumentException e) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Invalid period type '%s'. Valid values: %s".formatted(item.period(), getValidPeriodTypeValues())));
				continue;
			}

			String currencyCode = item.currency().toUpperCase();
			if (!assetRepository.existsByCodeIgnoreCase(currencyCode)) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Asset (currency) '%s' not found in the system".formatted(item.currency())));
				continue;
			}

			Category category = categoriesById.get(item.categoryId());
			if (category == null) {
				failures.add(new BulkBudgetImportFailureDto(
						item.budgetPlanName(), item.period(), item.amount(), item.currency(),
						"Category id '%s' not found in tracker".formatted(item.categoryId())));
				continue;
			}

			LocalDate validFrom = computePeriodStart(today, periodType);
			LocalDate validTo = computePeriodEnd(today, periodType);
			String planName = item.budgetPlanName();

			RecurringBudgetTemplate template = null;
			boolean recurringCreated = false;
			if (item.recurring()) {
				if (item.intervalValue() == null || item.intervalValue() <= 0) {
					failures.add(new BulkBudgetImportFailureDto(
							item.budgetPlanName(), item.period(), item.amount(), item.currency(),
							"Recurring budget requires positive intervalValue"));
					continue;
				}

				template = RecurringBudgetTemplate.builder()
						.expenseTracker(tracker)
						.name(planName)
						.amount(item.amount())
						.currencyCode(currencyCode)
						.periodType(periodType)
						.intervalValue(item.intervalValue())
						.startDate(validFrom)
						.nextRunDate(computeNextPeriodStart(validFrom, periodType, item.intervalValue()))
						.category(category)
						.build();
				template = recurringBudgetTemplateRepository.save(template);
				recurringCreated = true;
				log.info("Bulk import by categoryId: created recurring budget template '{}' ({}, interval {}) in tracker '{}'",
						planName, periodType, item.intervalValue(), tracker.getName());
			}

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
			log.info("Bulk import by categoryId: created budget plan '{}' ({}, {} — {}) in tracker '{}'",
					planName, periodType, validFrom, validTo, tracker.getName());

			successes.add(new BulkBudgetImportSuccessDto(
					planName,
					periodType.name(),
					item.amount(),
					currencyCode,
					category.getName(),
					true,
					recurringCreated));
		}

		log.info("Bulk import by categoryId complete for tracker '{}': {} succeeded, {} issues, {} request issues ignored",
				tracker.getName(), successes.size(), failures.size(), ignoredIssues);

		return new BulkBudgetImportResponseDto(
				items.size(), successes.size(), failures.size(), successes, failures);
	}

	private String getValidPeriodTypeValues() {
		return Arrays.stream(PeriodType.values())
				.map(Enum::name)
				.collect(Collectors.joining(", "));
	}

	private Category resolveCategoryForImport(
			UUID trackerId,
			String requestedCategoryName,
			Map<String, List<Category>> categoriesByNormalizedName,
			Map<String, List<Category>> categoriesByRawName
	) {
		String normalizedName = CustomUtilityString.normalize(requestedCategoryName);
		List<Category> normalizedMatches = categoriesByNormalizedName.getOrDefault(normalizedName, List.of());
		if (normalizedMatches.isEmpty()) {
			return null;
		}

		if (normalizedMatches.size() == 1) {
			return normalizedMatches.getFirst();
		}

		log.warn("Category lookup collision after normalization in tracker '{}': input='{}', normalized='{}', candidates={}",
				trackerId,
				requestedCategoryName,
				normalizedName,
				normalizedMatches.stream().map(Category::getName).toList());

		List<Category> rawMatches = categoriesByRawName.getOrDefault(requestedCategoryName.toLowerCase(Locale.ROOT), List.of());
		if (rawMatches.size() == 1) {
			log.info("Resolved category collision via exact-name lookup in tracker '{}': '{}' -> '{}'",
					trackerId, requestedCategoryName, rawMatches.getFirst().getName());
			return rawMatches.getFirst();
		}

		if (rawMatches.size() > 1) {
			log.warn("Category remains ambiguous after exact-name lookup in tracker '{}': input='{}', candidates={}",
					trackerId,
					requestedCategoryName,
					rawMatches.stream().map(Category::getName).toList());
		}

		return null;
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

	private LocalDate computeNextPeriodStart(LocalDate start, PeriodType periodType, int interval) {
		return switch (periodType) {
			case DAILY -> start.plusDays(interval);
			case WEEKLY -> start.with(java.time.DayOfWeek.MONDAY).plusWeeks(interval);
			case MONTHLY -> start.withDayOfMonth(1).plusMonths(interval);
			case QUARTERLY -> {
				int quarterMonth = ((start.getMonthValue() - 1) / 3) * 3 + 1;
				yield LocalDate.of(start.getYear(), quarterMonth, 1).plusMonths(3L * interval);
			}
			case YEARLY -> LocalDate.of(start.getYear(), Month.JANUARY, 1).plusYears(interval);
		};
	}

	private void addExportEntry(
			String budgetPlanName,
			PeriodType periodType,
			long amount,
			String currency,
			Category category,
			Integer intervalValue,
			boolean recurring,
			List<BulkBudgetExportItemDto> items,
			List<BulkBudgetExportIssueDto> issues
	) {
		String categoryName = category != null ? category.getName() : null;
		String period = periodType != null ? periodType.name() : null;

		if (category == null) {
			issues.add(new BulkBudgetExportIssueDto(
					budgetPlanName,
					period,
					amount,
					currency,
					null,
					intervalValue,
					recurring,
					"Category is not assigned"
			));
			return;
		}

		if (!category.isActive()) {
			issues.add(new BulkBudgetExportIssueDto(
					budgetPlanName,
					period,
					amount,
					currency,
					categoryName,
					intervalValue,
					recurring,
					"Category is inactive"
			));
			return;
		}

		items.add(new BulkBudgetExportItemDto(
				budgetPlanName,
				period,
				amount,
				currency,
				categoryName,
				intervalValue,
				recurring
		));
	}

	// ── Response builder ──

	private BudgetPlanResponseDto toResponseWithSpent(BudgetPlan plan) {
		long alreadySpent = budgetPlanSpentCalculator.computeAlreadySpent(plan);
		return budgetPlanMapper.toResponseWithSpent(plan, alreadySpent);
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