package org.leoric.expensetracker.utils;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BudgetPlanSpentCalculator {

	private final TransactionRepository transactionRepository;

	public long computeAlreadySpent(BudgetPlan plan) {
		if (plan.getCategory() == null) {
			return 0;
		}

		Set<UUID> categoryIds = new HashSet<>();
		collectCategoryIds(plan.getCategory(), categoryIds);

		TransactionType txType = plan.getCategory().getCategoryKind() == CategoryKind.INCOME
				? TransactionType.INCOME
				: TransactionType.EXPENSE;

		Instant from = plan.getValidFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant to = plan.getValidTo() != null
				? plan.getValidTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
				: Instant.now();

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
}