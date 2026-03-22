package org.leoric.expensetracker.budget.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.mapstruct.BudgetPlanMapper;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.budget.services.interfaces.BudgetPlanService;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateBudgetPlanNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetPlanServiceImpl implements BudgetPlanService {

	private final BudgetPlanRepository budgetPlanRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionRepository transactionRepository;
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

	// ── Response builder ──

	private BudgetPlanResponseDto toResponseWithSpent(BudgetPlan plan) {
		long alreadySpent = computeAlreadySpent(plan);
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

	private long computeAlreadySpent(BudgetPlan plan) {
		if (plan.getCategory() == null) {
			return 0;
		}

		// Collect this category + all descendants
		Set<UUID> categoryIds = new HashSet<>();
		collectCategoryIds(plan.getCategory(), categoryIds);

		// Determine transaction type from category kind
		TransactionType txType = plan.getCategory().getCategoryKind() == CategoryKind.INCOME
				? TransactionType.INCOME
				: TransactionType.EXPENSE;

		// Date range: validFrom (start of day UTC) to validTo+1 (exclusive) or now
		Instant from = plan.getValidFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant to;
		if (plan.getValidTo() != null) {
			to = plan.getValidTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		} else {
			to = Instant.now();
		}

		return transactionRepository.sumAmountByCategoryIdsAndDateRange(
				plan.getExpenseTracker().getId(), txType, categoryIds, from, to);
	}

	private void collectCategoryIds(Category category, Set<UUID> ids) {
		ids.add(category.getId());
		if (category.getChildren() != null) {
			for (Category child : category.getChildren()) {
				collectCategoryIds(child, ids);
			}
		}
	}

	// ── Helpers ──

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