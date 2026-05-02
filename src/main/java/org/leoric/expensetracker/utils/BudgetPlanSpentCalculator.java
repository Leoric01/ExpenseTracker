package org.leoric.expensetracker.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BudgetPlanSpentCalculator {

	private final TransactionRepository transactionRepository;
	private final AssetRepository assetRepository;
	private final ExchangeRateService exchangeRateService;
	private final CategoryRepository categoryRepository;

	public long computeAlreadySpent(BudgetPlan plan) {
		if (plan.getCategory() == null) {
			return 0;
		}

		Set<UUID> categoryIds = collectCategoryIds(plan.getExpenseTracker().getId(), plan.getCategory().getId());

		TransactionType txType = plan.getCategory().getCategoryKind() == CategoryKind.INCOME
				? TransactionType.INCOME
				: TransactionType.EXPENSE;

		Instant from = plan.getValidFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant to = plan.getValidTo() != null
				? plan.getValidTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
				: Instant.now();

		List<Transaction> transactions = transactionRepository.findCompletedByCategoryIdsAndDateRange(
				plan.getExpenseTracker().getId(), txType, categoryIds, from, to);

		Asset targetAsset = getAssetOrThrow(plan.getCurrencyCode());
		Map<String, Asset> assetByCode = new HashMap<>();
		assetByCode.put(normalizeCode(targetAsset.getCode()), targetAsset);

		long totalSpent = 0L;
		for (Transaction transaction : transactions) {
			Asset sourceAsset = resolveAsset(assetByCode, transaction.getCurrencyCode());
			long normalizedAmount = transaction.getAmount();

			if (!normalizeCode(sourceAsset.getCode()).equals(normalizeCode(targetAsset.getCode()))) {
				Long converted = exchangeRateService.convertAmount(
						transaction.getAmount(),
						sourceAsset,
						targetAsset,
						transaction.getTransactionDate());
				if (converted == null) {
					log.warn(
							"Missing exchange rate for transaction {} ({} -> {}, date={}); skipping amount in budget spent calculation",
							transaction.getId(),
							sourceAsset.getCode(),
							targetAsset.getCode(),
							transaction.getTransactionDate());
					continue;
				}
				normalizedAmount = converted;
			}

			totalSpent = Math.addExact(totalSpent, normalizedAmount);
		}

		return totalSpent;
	}

	private Asset resolveAsset(Map<String, Asset> assetByCode, String code) {
		String normalizedCode = normalizeCode(code);
		Asset cached = assetByCode.get(normalizedCode);
		if (cached != null) {
			return cached;
		}

		Asset loaded = getAssetOrThrow(code);
		assetByCode.put(normalizeCode(loaded.getCode()), loaded);
		return loaded;
	}

	private Asset getAssetOrThrow(String code) {
		return assetRepository.findByCodeIgnoreCase(code)
				.orElseThrow(() -> new IllegalStateException("Asset not found for code: " + code));
	}

	private String normalizeCode(String code) {
		return code == null ? "" : code.trim().toUpperCase();
	}

	private Set<UUID> collectCategoryIds(UUID trackerId, UUID rootCategoryId) {
		List<Category> activeCategories = categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);
		Map<UUID, List<UUID>> childrenByParentId = new HashMap<>();

		for (Category category : activeCategories) {
			if (category.getParent() == null) {
				continue;
			}
			childrenByParentId
					.computeIfAbsent(category.getParent().getId(), ignored -> new java.util.ArrayList<>())
					.add(category.getId());
		}

		Set<UUID> ids = new HashSet<>();
		ArrayDeque<UUID> stack = new ArrayDeque<>();
		stack.push(rootCategoryId);

		while (!stack.isEmpty()) {
			UUID current = stack.pop();
			if (!ids.add(current)) {
				continue;
			}

			for (UUID childId : childrenByParentId.getOrDefault(current, List.of())) {
				stack.push(childId);
			}
		}

		return ids;
	}
}