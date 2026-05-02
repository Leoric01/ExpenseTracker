package org.leoric.expensetracker.category.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.budget.dto.CategoryActiveBudgetPlanDto;
import org.leoric.expensetracker.budget.mapstruct.BudgetPlanMapper;
import org.leoric.expensetracker.category.dto.CategoryActiveTreeResponseDto;
import org.leoric.expensetracker.category.dto.CategoryBulkExportResponseDto;
import org.leoric.expensetracker.category.dto.CategoryMovementAssetTotalsDto;
import org.leoric.expensetracker.category.dto.CategoryMovementConvertedTotalsDto;
import org.leoric.expensetracker.category.dto.CategoryMovementSummaryResponseDto;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.dto.CategoryResponseDto;
import org.leoric.expensetracker.category.dto.CreateCategoryBulkRequestDto;
import org.leoric.expensetracker.category.dto.CreateCategoryRequestDto;
import org.leoric.expensetracker.category.dto.UpdateCategoryRequestDto;
import org.leoric.expensetracker.category.mapstruct.CategoryMapper;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.category.services.interfaces.CategoryService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.handler.exceptions.CategoryHasActiveChildrenException;
import org.leoric.expensetracker.handler.exceptions.DuplicateCategoryNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.utils.BudgetPlanSpentCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
	private static final int ACTIVE_TREE_MAX_ITEMS = 1000;

	private final BudgetPlanSpentCalculator budgetPlanSpentCalculator;
	private final CategoryRepository categoryRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final BudgetPlanRepository budgetPlanRepository;
	private final TransactionRepository transactionRepository;
	private final AssetRepository assetRepository;
	private final ExchangeRateService exchangeRateService;
	private final CategoryMapper categoryMapper;
	private final BudgetPlanMapper budgetPlanMapper;
	private final ImageService imageService;

	@Override
	@Transactional
	public CategoryResponseDto categoryCreate(User currentUser, UUID trackerId, CreateCategoryRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		Category parent = null;
		if (request.parentId() != null) {
			parent = getCategoryOrThrow(request.parentId());
			assertCategoryBelongsToTracker(parent, trackerId);
			assertCategoryKindMatchesParent(request.categoryKind(), parent);
		}

		UUID parentId = parent != null ? parent.getId() : null;
		if (categoryRepository.existsByExpenseTrackerIdAndParentIdAndNameIgnoreCase(trackerId, parentId, request.name())) {
			throw new DuplicateCategoryNameException(
					"Category with name '%s' already exists at this level".formatted(request.name()));
		}

		Category category = Category.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.categoryKind(request.categoryKind())
				.parent(parent)
				.sortOrder(request.sortOrder())
				.build();

		category = categoryRepository.save(category);
		log.info("User {} created category '{}' in tracker '{}'", currentUser.getEmail(), category.getName(), tracker.getName());

		return categoryMapper.toResponse(category);
	}

	@Override
	@Transactional
	public List<CategoryResponseDto> categoryCreateBulk(User currentUser, UUID trackerId, List<CreateCategoryBulkRequestDto> request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		List<Category> allCategories = new ArrayList<>();
		Set<String> duplicateCheck = new HashSet<>();

		for (CreateCategoryBulkRequestDto rootDto : request) {
			flattenTree(rootDto, null, rootDto.categoryKind(), tracker, trackerId, allCategories, duplicateCheck);
		}

		categoryRepository.saveAll(allCategories);

		List<Category> roots = allCategories.stream()
				.filter(c -> c.getParent() == null)
				.toList();

		log.info("User {} bulk-created {} categories in tracker '{}'",
		         currentUser.getEmail(), allCategories.size(), tracker.getName());

		return roots.stream()
				.map(categoryMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<CategoryBulkExportResponseDto> categoryExportBulk(User currentUser, UUID trackerId) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		List<Category> activeCategories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		Map<UUID, List<Category>> categoriesByParentId = new HashMap<>();
		for (Category category : activeCategories) {
			UUID parentId = category.getParent() != null ? category.getParent().getId() : null;
			categoriesByParentId
					.computeIfAbsent(parentId, ignored -> new ArrayList<>())
					.add(category);
		}

		for (List<Category> siblings : categoriesByParentId.values()) {
			siblings.sort(CATEGORY_EXPORT_COMPARATOR);
		}

		List<CategoryBulkExportResponseDto> exportPayload = toBulkExportTree(null, categoriesByParentId);

		log.info("User {} exported {} active categories in tracker '{}' for bulk import",
				currentUser.getEmail(), activeCategories.size(), tracker.getName());

		return exportPayload;
	}

	private void flattenTree(CreateCategoryBulkRequestDto dto, Category parent, CategoryKind rootKind,
			ExpenseTracker tracker, UUID trackerId, List<Category> collector, Set<String> duplicateCheck) {
		if (dto.categoryKind() != rootKind) {
			throw new OperationNotPermittedException(
					"Category '%s' has kind '%s' but the root of this branch is '%s'. All categories in a branch must share the same kind"
							.formatted(dto.name(), dto.categoryKind(), rootKind));
		}

		UUID parentId = parent != null ? parent.getId() : null;
		String dupeKey = trackerId + "|" + parentId + "|" + dto.name().toLowerCase();
		if (!duplicateCheck.add(dupeKey)) {
			throw new DuplicateCategoryNameException(
					"Category with name '%s' already exists at this level".formatted(dto.name()));
		}

		if (categoryRepository.existsByExpenseTrackerIdAndParentIdAndNameIgnoreCase(trackerId, parentId, dto.name())) {
			throw new DuplicateCategoryNameException(
					"Category with name '%s' already exists at this level".formatted(dto.name()));
		}

		Category category = Category.builder()
				.expenseTracker(tracker)
				.name(dto.name())
				.categoryKind(rootKind)
				.parent(parent)
				.sortOrder(dto.sortOrder())
				.build();

		if (parent != null) {
			parent.getChildren().add(category);
		}

		collector.add(category);

		for (CreateCategoryBulkRequestDto childDto : dto.children()) {
			flattenTree(childDto, category, rootKind, tracker, trackerId, collector, duplicateCheck);
		}
	}

	private static final Comparator<Category> CATEGORY_EXPORT_COMPARATOR = Comparator
			.comparing(Category::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER);

	private static final Comparator<CategoryActiveTreeResponseDto> CATEGORY_ACTIVE_TREE_COMPARATOR = Comparator
			.comparing(CategoryActiveTreeResponseDto::sortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(dto -> dto.name() == null ? "" : dto.name(), String.CASE_INSENSITIVE_ORDER);

	private List<CategoryBulkExportResponseDto> toBulkExportTree(UUID parentId, Map<UUID, List<Category>> categoriesByParentId) {
		return categoriesByParentId.getOrDefault(parentId, List.of()).stream()
				.map(category -> new CategoryBulkExportResponseDto(
						category.getName(),
						category.getCategoryKind(),
						category.getSortOrder(),
						toBulkExportTree(category.getId(), categoriesByParentId)
				))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CategoryResponseDto categoryFindById(User currentUser, UUID trackerId, UUID categoryId) {
		Category category = getCategoryOrThrow(categoryId);
		assertCategoryBelongsToTracker(category, trackerId);
		return categoryMapper.toResponse(category);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<CategoryResponseDto> categoryFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return categoryRepository.findRootsByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(categoryMapper::toResponse);
		}
		return categoryRepository.findByExpenseTrackerIdAndActiveTrueAndParentIsNull(trackerId, pageable)
				.map(categoryMapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<CategoryResponseDto> categoryFindAllActive(User currentUser, UUID trackerId, String search, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
		LocalDate today = LocalDate.now();
		log.debug("categoryFindAllActive called — tracker={}, search='{}', dateFrom={}, dateTo={}, today={}",
				trackerId, search, dateFrom, dateTo, today);

		List<BudgetPlan> currentActivePlans = budgetPlanRepository
				.findAllCurrentActiveByExpenseTrackerIdWithCategory(trackerId, today);
		log.debug("Found {} currently active budget plans (today={})", currentActivePlans.size(), today);

		Map<UUID, BudgetPlan> activeBudgetPlansByCategoryId = currentActivePlans
				.stream()
				.collect(Collectors.toMap(
						plan -> plan.getCategory().getId(),
						Function.identity(),
						(existing, duplicate) -> {
							BudgetPlan chosen = choosePreferredBudgetPlan(existing, duplicate);
							BudgetPlan discarded = chosen == existing ? duplicate : existing;

							log.warn(
									"Multiple active budget plans found for category {} in tracker {}. Keeping budget plan {}, ignoring {}",
									chosen.getCategory().getId(),
									trackerId,
									chosen.getId(),
									discarded.getId()
							);

							return chosen;
						}
				));
		log.debug("Mapped {} categories with an activeBudgetPlan", activeBudgetPlansByCategoryId.size());

		Map<UUID, List<BudgetPlan>> budgetPlansInRangeByCategoryId;
		if (dateFrom != null && dateTo != null) {
			List<BudgetPlan> rangePlans = budgetPlanRepository
					.findAllActiveByExpenseTrackerIdWithCategoryInRange(trackerId, dateFrom, dateTo);
			log.debug("Found {} budget plans overlapping range [{} — {}]", rangePlans.size(), dateFrom, dateTo);
			if (log.isTraceEnabled()) {
				rangePlans.forEach(p -> log.trace("  range plan: id={}, name='{}', categoryId={}, categoryName='{}', validFrom={}, validTo={}",
						p.getId(), p.getName(),
						p.getCategory().getId(), p.getCategory().getName(),
						p.getValidFrom(), p.getValidTo()));
			}
			budgetPlansInRangeByCategoryId = rangePlans.stream()
					.collect(Collectors.groupingBy(plan -> plan.getCategory().getId()));
			log.debug("Grouped range plans into {} categories", budgetPlansInRangeByCategoryId.size());
		} else {
			budgetPlansInRangeByCategoryId = Map.of();
			log.debug("No dateFrom/dateTo provided — skipping range budget plan query");
		}

		if (search != null && !search.isBlank()) {
			return categoryRepository.findActiveByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(category -> toFlatResponse(category, activeBudgetPlansByCategoryId, budgetPlansInRangeByCategoryId));
		}

		return categoryRepository.findByExpenseTrackerIdAndActiveTrueAndParentIsNull(trackerId, pageable)
				.map(category -> toActiveResponse(category, activeBudgetPlansByCategoryId, budgetPlansInRangeByCategoryId));
	}

	@Override
	@Transactional(readOnly = true)
	public CategoryMovementSummaryResponseDto categoryMovementSummary(User currentUser, UUID trackerId, Instant dateFrom, Instant dateTo, String displayAssetCode) {
		if (dateFrom == null || dateTo == null) {
			throw new OperationNotPermittedException("dateFrom and dateTo are required");
		}
		if (dateFrom.isAfter(dateTo)) {
			throw new OperationNotPermittedException("dateFrom must be before or equal to dateTo");
		}

		LocalDate rangeFrom = dateFrom.atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate rangeTo = dateTo.atZone(ZoneOffset.UTC).toLocalDate();
		log.debug("categoryMovementSummary called: trackerId={}, dateFrom={}, dateTo={}, displayAssetCode='{}'", trackerId, dateFrom, dateTo, displayAssetCode);

		List<Category> activeCategories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		Set<UUID> rootCategoryIds = activeCategories.stream()
				.filter(category -> category.getParent() == null)
				.map(Category::getId)
				.collect(Collectors.toSet());

		Map<UUID, CategoryKind> rootKindById = activeCategories.stream()
				.filter(category -> category.getParent() == null)
				.collect(Collectors.toMap(Category::getId, Category::getCategoryKind));

		Map<String, AssetSummaryTotals> totalsByAssetCode = new HashMap<>();
		Map<String, Asset> assetCacheByCode = new HashMap<>();
		Asset displayAsset = resolveDisplayAsset(displayAssetCode);
		LocalDate rateDate = LocalDate.now(ZoneOffset.UTC);
		long convertedExpectedExpense = 0L;
		long convertedExpectedIncome = 0L;
		long convertedActualExpense = 0L;
		long convertedActualIncome = 0L;
		boolean conversionComplete = displayAsset != null;
		int conversionAttempts = 0;
		int conversionFailures = 0;

		List<BudgetPlan> plans = budgetPlanRepository.findAllActiveByExpenseTrackerIdWithCategoryInRange(trackerId, rangeFrom, rangeTo);
		log.debug("categoryMovementSummary budget plans in range: trackerId={}, plansCount={}", trackerId, plans.size());
		for (BudgetPlan plan : plans) {
			Category category = plan.getCategory();
			if (category == null || category.getParent() != null) {
				continue;
			}
			if (!rootCategoryIds.contains(category.getId())) {
				continue;
			}

			AssetSummaryTotals totals = totalsByAssetCode.computeIfAbsent(normalizeCode(plan.getCurrencyCode()), ignored -> new AssetSummaryTotals());
			if (category.getCategoryKind() == CategoryKind.INCOME) {
				totals.expectedIncome += plan.getAmount();
			} else {
				totals.expectedExpense += plan.getAmount();
			}

			if (displayAsset != null) {
				conversionAttempts++;
				Long converted = convertToDisplayAmount(plan.getAmount(), plan.getCurrencyCode(), plan.getValidFrom().atStartOfDay().toInstant(ZoneOffset.UTC), displayAsset, assetCacheByCode);
				if (converted == null) {
					conversionComplete = false;
					conversionFailures++;
				} else if (category.getCategoryKind() == CategoryKind.INCOME) {
					convertedExpectedIncome = Math.addExact(convertedExpectedIncome, converted);
				} else {
					convertedExpectedExpense = Math.addExact(convertedExpectedExpense, converted);
				}
			}
		}

		List<Transaction> completedTransactions = transactionRepository.findCompletedByExpenseTrackerIdAndDateRange(trackerId, dateFrom, dateTo);
		log.debug("categoryMovementSummary completed transactions in range: trackerId={}, txCount={}", trackerId, completedTransactions.size());
		for (Transaction transaction : completedTransactions) {
			if (transaction.getTransactionType() == TransactionType.INCOME || transaction.getTransactionType() == TransactionType.EXPENSE) {
				Category category = transaction.getCategory();
				UUID rootId = findRootCategoryId(category);
				if (rootId == null || !rootKindById.containsKey(rootId)) {
					continue;
				}

				AssetSummaryTotals totals = totalsByAssetCode.computeIfAbsent(normalizeCode(transaction.getCurrencyCode()), ignored -> new AssetSummaryTotals());
				if (transaction.getTransactionType() == TransactionType.INCOME) {
					totals.actualIncome += transaction.getAmount();
				} else {
					totals.actualExpense += transaction.getAmount();
				}

				if (displayAsset != null) {
					conversionAttempts++;
					Long converted = convertToDisplayAmount(transaction.getAmount(), transaction.getCurrencyCode(), transaction.getTransactionDate(), displayAsset, assetCacheByCode);
					if (converted == null) {
						conversionComplete = false;
						conversionFailures++;
					} else if (transaction.getTransactionType() == TransactionType.INCOME) {
						convertedActualIncome = Math.addExact(convertedActualIncome, converted);
					} else {
						convertedActualExpense = Math.addExact(convertedActualExpense, converted);
					}
				}
				continue;
			}

			if (transaction.getTransactionType() == TransactionType.TRANSFER) {
				String sourceAssetCode = transaction.getSourceHolding() != null && transaction.getSourceHolding().getAsset() != null
						? transaction.getSourceHolding().getAsset().getCode()
						: transaction.getCurrencyCode();
				String targetAssetCode = transaction.getTargetHolding() != null && transaction.getTargetHolding().getAsset() != null
						? transaction.getTargetHolding().getAsset().getCode()
						: null;

				if (sourceAssetCode == null || targetAssetCode == null || sourceAssetCode.equalsIgnoreCase(targetAssetCode) || transaction.getFeeAmount() <= 0) {
					continue;
				}

				AssetSummaryTotals totals = totalsByAssetCode.computeIfAbsent(normalizeCode(sourceAssetCode), ignored -> new AssetSummaryTotals());
				totals.actualExpense += transaction.getFeeAmount();

				if (displayAsset != null) {
					conversionAttempts++;
					Long converted = convertToDisplayAmount(transaction.getFeeAmount(), sourceAssetCode, transaction.getTransactionDate(), displayAsset, assetCacheByCode);
					if (converted == null) {
						conversionComplete = false;
						conversionFailures++;
					} else {
						convertedActualExpense = Math.addExact(convertedActualExpense, converted);
					}
				}
			}
		}

		List<CategoryMovementAssetTotalsDto> nativeByAsset = totalsByAssetCode.entrySet().stream()
				.map(entry -> {
					String assetCode = entry.getKey();
					AssetSummaryTotals totals = entry.getValue();
					Asset asset = resolveAsset(assetCode, assetCacheByCode);
					BigDecimal exchangeRate = null;
					if (displayAsset != null && asset != null) {
						if (asset.getCode().equalsIgnoreCase(displayAsset.getCode())) {
							exchangeRate = BigDecimal.ONE;
						} else {
							exchangeRate = exchangeRateService.getRate(asset, displayAsset, rateDate);
						}
					}
					return new CategoryMovementAssetTotalsDto(
							assetCode,
							asset != null ? asset.getScale() : null,
							exchangeRate,
							totals.expectedExpense,
							totals.expectedIncome,
							totals.expectedIncome - totals.expectedExpense,
							totals.actualExpense,
							totals.actualIncome,
							totals.actualIncome - totals.actualExpense
					);
				})
				.sorted(Comparator.comparing(CategoryMovementAssetTotalsDto::assetCode))
				.toList();

		CategoryMovementConvertedTotalsDto convertedTotals;
		if (displayAsset == null) {
			convertedTotals = new CategoryMovementConvertedTotalsDto(
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null
			);
		} else {
			if (!conversionComplete) {
				log.warn("categoryMovementSummary conversion incomplete: trackerId={}, attempts={}, failures={}, targetAsset={}",
						trackerId, conversionAttempts, conversionFailures, displayAsset.getCode());
			}
			log.debug("categoryMovementSummary converted totals: trackerId={}, attempts={}, failures={}, expectedExpense={}, expectedIncome={}, actualExpense={}, actualIncome={}, complete={}",
					trackerId, conversionAttempts, conversionFailures, convertedExpectedExpense, convertedExpectedIncome, convertedActualExpense, convertedActualIncome, conversionComplete);
			convertedTotals = new CategoryMovementConvertedTotalsDto(
					displayAsset.getCode(),
					displayAsset.getScale(),
					conversionComplete ? convertedExpectedExpense : null,
					conversionComplete ? convertedExpectedIncome : null,
					conversionComplete ? convertedExpectedIncome - convertedExpectedExpense : null,
					conversionComplete ? convertedActualExpense : null,
					conversionComplete ? convertedActualIncome : null,
					conversionComplete ? convertedActualIncome - convertedActualExpense : null
			);
		}

		return new CategoryMovementSummaryResponseDto(dateFrom, dateTo, nativeByAsset, convertedTotals);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CategoryActiveTreeResponseDto> categoryFindAllActiveTree(User currentUser, UUID trackerId) {
		LocalDate today = LocalDate.now();

		List<Category> activeCategories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		if (activeCategories.size() > ACTIVE_TREE_MAX_ITEMS) {
			log.warn("Active category tree exceeds limit for tracker {}: {} items (limit {}).", trackerId, activeCategories.size(), ACTIVE_TREE_MAX_ITEMS);
			throw new OperationNotPermittedException("Active category tree is too large. Limit is %d items including subcategories".formatted(ACTIVE_TREE_MAX_ITEMS));
		}

		Map<UUID, BudgetPlan> activeBudgetPlansByCategoryId = budgetPlanRepository
				.findAllCurrentActiveByExpenseTrackerIdWithCategory(trackerId, today)
				.stream()
				.collect(Collectors.toMap(
						plan -> plan.getCategory().getId(),
						Function.identity(),
						(existing, duplicate) -> {
							log.warn(
									"Multiple active budget plans found for category {} in tracker {}. Keeping first found budget plan {}, ignoring {}",
									existing.getCategory().getId(),
									trackerId,
									existing.getId(),
									duplicate.getId()
							);
							return existing;
						}
				));

		Map<UUID, CategoryActiveTreeResponseDto> dtoById = new HashMap<>();
		for (Category category : activeCategories) {
			BudgetPlan activePlan = activeBudgetPlansByCategoryId.get(category.getId());
			dtoById.put(category.getId(), toActiveTreeItem(category, activePlan));
		}

		Map<UUID, List<CategoryActiveTreeResponseDto>> childrenByParentId = new HashMap<>();
		List<CategoryActiveTreeResponseDto> roots = new ArrayList<>();

		for (Category category : activeCategories) {
			CategoryActiveTreeResponseDto current = dtoById.get(category.getId());
			UUID parentId = category.getParent() != null ? category.getParent().getId() : null;

			if (parentId == null || !dtoById.containsKey(parentId)) {
				roots.add(current);
				continue;
			}

			childrenByParentId.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(current);
		}

		return toActiveTree(roots, childrenByParentId);
	}

	private BudgetPlan choosePreferredBudgetPlan(BudgetPlan left, BudgetPlan right) {
		if (left.getValidFrom().isAfter(right.getValidFrom())) {
			return left;
		}
		if (right.getValidFrom().isAfter(left.getValidFrom())) {
			return right;
		}

		if (left.getLastModifiedDate() != null && right.getLastModifiedDate() != null) {
			if (left.getLastModifiedDate().isAfter(right.getLastModifiedDate())) {
				return left;
			}
			if (right.getLastModifiedDate().isAfter(left.getLastModifiedDate())) {
				return right;
			}
		}

		return left;
	}

	private List<CategoryActiveTreeResponseDto> toActiveTree(
			List<CategoryActiveTreeResponseDto> categories,
			Map<UUID, List<CategoryActiveTreeResponseDto>> childrenByParentId
	) {
		return categories.stream()
				.sorted(CATEGORY_ACTIVE_TREE_COMPARATOR)
				.map(category -> new CategoryActiveTreeResponseDto(
						category.id(),
						category.name(),
						category.categoryKind(),
						category.parentId(),
						category.parentName(),
						category.sortOrder(),
						category.budgetPlanId(),
						category.budgetPlanName(),
						category.assetCode(),
						toActiveTree(childrenByParentId.getOrDefault(category.id(), List.of()), childrenByParentId)
				))
				.toList();
	}

	private CategoryActiveTreeResponseDto toActiveTreeItem(Category category, BudgetPlan activePlan) {
		return new CategoryActiveTreeResponseDto(
				category.getId(),
				category.getName(),
				category.getCategoryKind(),
				category.getParent() != null ? category.getParent().getId() : null,
				category.getParent() != null ? category.getParent().getName() : null,
				category.getSortOrder(),
				activePlan != null ? activePlan.getId() : null,
				activePlan != null ? activePlan.getName() : null,
				activePlan != null ? activePlan.getCurrencyCode() : null,
				List.of()
		);
	}

	@Override
	@Transactional
	public CategoryResponseDto categoryUpdate(User currentUser, UUID trackerId, UUID categoryId, UpdateCategoryRequestDto request) {
		Category category = getCategoryOrThrow(categoryId);
		assertCategoryBelongsToTracker(category, trackerId);

		if (request.parentId() != null) {
			Category newParent = getCategoryOrThrow(request.parentId());
			assertCategoryBelongsToTracker(newParent, trackerId);
			if (newParent.getId().equals(categoryId)) {
				throw new OperationNotPermittedException("A category cannot be its own parent");
			}
			CategoryKind effectiveKind = request.categoryKind() != null ? request.categoryKind() : category.getCategoryKind();
			assertCategoryKindMatchesParent(effectiveKind, newParent);
			category.setParent(newParent);
		}

		if (request.categoryKind() != null && request.categoryKind() != category.getCategoryKind()) {
			if (category.getParent() != null && request.parentId() == null) {
				assertCategoryKindMatchesParent(request.categoryKind(), category.getParent());
			}
			cascadeCategoryKindToChildren(category, request.categoryKind());
		}

		categoryMapper.updateFromDto(request, category);
		category = categoryRepository.save(category);

		log.info("User {} updated category '{}' in tracker '{}'",
		         currentUser.getEmail(), category.getName(), category.getExpenseTracker().getName());
		return categoryMapper.toResponse(category);
	}

	@Override
	@Transactional
	public void categoryDeactivate(User currentUser, UUID trackerId, UUID categoryId, boolean cascade) {
		Category category = getCategoryOrThrow(categoryId);
		assertCategoryBelongsToTracker(category, trackerId);

		if (!category.isActive()) {
			throw new OperationNotPermittedException("Category is already deactivated");
		}

		boolean hasActiveChildren = category.getChildren().stream().anyMatch(Category::isActive);

		if (hasActiveChildren && !cascade) {
			throw new CategoryHasActiveChildrenException(
					"Category '%s' has active subcategories. Use cascade=true to deactivate them as well".formatted(category.getName()));
		}

		deactivateRecursively(category);
		log.info("User {} deactivated category '{}' (cascade={}) in tracker '{}'",
		         currentUser.getEmail(), category.getName(), cascade, category.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public CategoryResponseDto categoryUploadIcon(User currentUser, UUID trackerId, UUID categoryId, MultipartFile icon, String iconColor) {
		Category category = getCategoryOrThrow(categoryId);
		assertCategoryBelongsToTracker(category, trackerId);

		String iconUrl = imageService.uploadImage(icon, "expense-tracker/categories");
		category.setIconUrl(iconUrl);
		category.setIconColor(iconColor);
		category = categoryRepository.save(category);

		log.info("User {} uploaded icon for category '{}' in tracker '{}'",
		         currentUser.getEmail(), category.getName(), category.getExpenseTracker().getName());
		return categoryMapper.toResponse(category);
	}

	@Override
	@Transactional
	public CategoryResponseDto categoryDeleteIcon(User currentUser, UUID trackerId, UUID categoryId) {
		Category category = getCategoryOrThrow(categoryId);
		assertCategoryBelongsToTracker(category, trackerId);

		category.setIconUrl(null);
		category.setIconColor(null);
		category = categoryRepository.save(category);

		log.info("User {} deleted icon for category '{}' in tracker '{}'",
		         currentUser.getEmail(), category.getName(), category.getExpenseTracker().getName());
		return categoryMapper.toResponse(category);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}

	private void assertCategoryKindMatchesParent(CategoryKind childKind, Category parent) {
		if (childKind != parent.getCategoryKind()) {
			throw new OperationNotPermittedException(
					"Category kind '%s' does not match parent's kind '%s'".formatted(childKind, parent.getCategoryKind()));
		}
	}

	private void cascadeCategoryKindToChildren(Category category, CategoryKind newKind) {
		for (Category child : category.getChildren()) {
			child.setCategoryKind(newKind);
			categoryRepository.save(child);
			cascadeCategoryKindToChildren(child, newKind);
		}
	}

	private void deactivateRecursively(Category category) {
		category.setActive(false);
		categoryRepository.save(category);
		for (Category child : category.getChildren()) {
			if (child.isActive()) {
				deactivateRecursively(child);
			}
		}
	}

	private CategoryResponseDto toActiveResponse(Category category, Map<UUID, BudgetPlan> activeBudgetPlansByCategoryId, Map<UUID, List<BudgetPlan>> budgetPlansInRangeByCategoryId) {
		CategoryResponseDto mapped = categoryMapper.toResponse(category);

		CategoryActiveBudgetPlanDto activeBudgetPlan = mapActiveBudgetPlan(category, activeBudgetPlansByCategoryId);
		List<CategoryActiveBudgetPlanDto> budgetPlansForSelectedPeriod = mapBudgetPlansInRange(category, budgetPlansInRangeByCategoryId);

		List<CategoryResponseDto> activeChildren = category.getChildren().stream()
				.filter(Category::isActive)
				.map(child -> toActiveResponse(child, activeBudgetPlansByCategoryId, budgetPlansInRangeByCategoryId))
				.toList();

		return new CategoryResponseDto(
				mapped.id(),
				mapped.name(),
				mapped.categoryKind(),
				mapped.parentId(),
				mapped.parentName(),
				mapped.sortOrder(),
				mapped.active(),
				mapped.iconUrl(),
				mapped.iconColor(),
				activeBudgetPlan,
				budgetPlansForSelectedPeriod,
				activeChildren,
				mapped.createdDate(),
				mapped.lastModifiedDate()
		);
	}

	private CategoryResponseDto toFlatResponse(Category category, Map<UUID, BudgetPlan> activeBudgetPlansByCategoryId, Map<UUID, List<BudgetPlan>> budgetPlansInRangeByCategoryId) {
		CategoryResponseDto mapped = categoryMapper.toFlatResponse(category);

		CategoryActiveBudgetPlanDto activeBudgetPlan = mapActiveBudgetPlan(category, activeBudgetPlansByCategoryId);
		List<CategoryActiveBudgetPlanDto> budgetPlansForSelectedPeriod = mapBudgetPlansInRange(category, budgetPlansInRangeByCategoryId);

		return new CategoryResponseDto(
				mapped.id(),
				mapped.name(),
				mapped.categoryKind(),
				mapped.parentId(),
				mapped.parentName(),
				mapped.sortOrder(),
				mapped.active(),
				mapped.iconUrl(),
				mapped.iconColor(),
				activeBudgetPlan,
				budgetPlansForSelectedPeriod,
				mapped.children(),
				mapped.createdDate(),
				mapped.lastModifiedDate()
		);
	}

	private CategoryActiveBudgetPlanDto mapActiveBudgetPlan(Category category, Map<UUID, BudgetPlan> activeBudgetPlansByCategoryId) {
		BudgetPlan plan = activeBudgetPlansByCategoryId.get(category.getId());
		return plan == null ? null : toCategoryActiveBudgetPlanResponse(plan);
	}

	private UUID findRootCategoryId(Category category) {
		if (category == null) {
			return null;
		}

		Category current = category;
		while (current.getParent() != null) {
			current = current.getParent();
		}
		return current.getId();
	}

	private Asset resolveDisplayAsset(String displayAssetCode) {
		if (displayAssetCode == null || displayAssetCode.isBlank()) {
			return null;
		}
		return assetRepository.findByCodeIgnoreCase(displayAssetCode)
				.orElseThrow(() -> new OperationNotPermittedException("Asset code '%s' does not exist".formatted(displayAssetCode)));
	}

	private String normalizeCode(String code) {
		return code == null ? "" : code.trim().toUpperCase();
	}

	private Asset resolveAsset(String assetCode, Map<String, Asset> cacheByCode) {
		String normalized = normalizeCode(assetCode);
		if (normalized.isEmpty()) {
			return null;
		}

		Asset cached = cacheByCode.get(normalized);
		if (cached != null) {
			return cached;
		}

		Asset loaded = assetRepository.findByCodeIgnoreCase(normalized).orElse(null);
		if (loaded != null) {
			cacheByCode.put(normalized, loaded);
		}
		return loaded;
	}

	private Long convertToDisplayAmount(long amount, String sourceAssetCode, Instant at, Asset displayAsset, Map<String, Asset> cacheByCode) {
		if (displayAsset == null) {
			return null;
		}

		String sourceCodeNormalized = normalizeCode(sourceAssetCode);
		if (sourceCodeNormalized.isEmpty()) {
			return null;
		}

		if (displayAsset.getCode().equalsIgnoreCase(sourceCodeNormalized)) {
			return amount;
		}

		Asset sourceAsset = resolveAsset(sourceCodeNormalized, cacheByCode);
		if (sourceAsset == null) {
			log.warn("Cannot convert amount because source asset '{}' is missing", sourceAssetCode);
			return null;
		}

		Long converted = exchangeRateService.convertAmount(amount, sourceAsset, displayAsset, at);
		if (converted == null) {
			log.warn("Cannot convert amount from {} to {} at {} due to missing exchange rate", sourceAsset.getCode(), displayAsset.getCode(), at);
		}
		return converted;
	}

	private CategoryActiveBudgetPlanDto toCategoryActiveBudgetPlanResponse(BudgetPlan plan) {
		long alreadySpent = budgetPlanSpentCalculator.computeAlreadySpent(plan);
		CategoryActiveBudgetPlanDto mapped = budgetPlanMapper.toCategoryActiveBudgetPlanDto(plan);

		return new CategoryActiveBudgetPlanDto(
				mapped.id(),
				mapped.name(),
				mapped.amount(),
				mapped.currencyCode(),
				mapped.periodType(),
				mapped.validFrom(),
				mapped.validTo(),
				mapped.active(),
				alreadySpent,
				mapped.createdDate(),
				mapped.lastModifiedDate()
		);
	}

	private List<CategoryActiveBudgetPlanDto> mapBudgetPlansInRange(Category category, Map<UUID, List<BudgetPlan>> budgetPlansInRangeByCategoryId) {
		List<BudgetPlan> plans = budgetPlansInRangeByCategoryId.get(category.getId());
		if (plans == null || plans.isEmpty()) {
			return null;
		}
		return plans.stream()
				.map(this::toCategoryActiveBudgetPlanResponse)
				.toList();
	}

	private static final class AssetSummaryTotals {
		private long expectedExpense;
		private long expectedIncome;
		private long actualExpense;
		private long actualIncome;
	}
}