package org.leoric.expensetracker.transaction;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TransactionSpecification {
	private TransactionSpecification() {
	}

	public static Specification<Transaction> filter(
			UUID trackerId,
			TransactionFilter filter,
			Set<UUID> explicitCategoryIds,
			Set<UUID> searchCategoryIds
	) {
		return (root, _, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			Join<Transaction, Holding> holdingJoin = root.join("holding", JoinType.LEFT);
			Join<Transaction, Holding> sourceHoldingJoin = root.join("sourceHolding", JoinType.LEFT);
			Join<Transaction, Holding> targetHoldingJoin = root.join("targetHolding", JoinType.LEFT);
			Join<Holding, Account> holdingAccountJoin = holdingJoin.join("account", JoinType.LEFT);
			Join<Holding, Account> sourceHoldingAccountJoin = sourceHoldingJoin.join("account", JoinType.LEFT);
			Join<Holding, Account> targetHoldingAccountJoin = targetHoldingJoin.join("account", JoinType.LEFT);
			Join<Transaction, Category> categoryJoin = root.join("category", JoinType.LEFT);

			predicates.add(cb.equal(root.get("expenseTracker").get("id"), trackerId));

			if (filter.holdingId() != null) {
				predicates.add(cb.or(
						cb.equal(holdingJoin.get("id"), filter.holdingId()),
						cb.equal(sourceHoldingJoin.get("id"), filter.holdingId()),
						cb.equal(targetHoldingJoin.get("id"), filter.holdingId())
				));
			}

			if (filter.transactionType() != null) {
				predicates.add(cb.equal(root.get("transactionType"), filter.transactionType()));
			}

			if (filter.status() != null) {
				predicates.add(cb.equal(root.get("status"), filter.status()));
			}

			if (filter.dateFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.dateFrom()));
			}

			if (filter.dateTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.dateTo()));
			}

			if (!explicitCategoryIds.isEmpty()) {
				predicates.add(categoryJoin.get("id").in(explicitCategoryIds));
			}

			if (filter.search() != null && !filter.search().isBlank()) {
				String like = "%" + filter.search().toLowerCase() + "%";

				List<Predicate> searchPredicates = new ArrayList<>();
				searchPredicates.add(likeNullable(cb, root.get("description"), like));
				searchPredicates.add(likeNullable(cb, root.get("note"), like));
				searchPredicates.add(likeNullable(cb, root.get("currencyCode"), like));
				searchPredicates.add(cb.like(cb.lower(root.get("transactionType").as(String.class)), like));
				searchPredicates.add(cb.like(cb.lower(root.get("status").as(String.class)), like));
				searchPredicates.add(likeNullable(cb, holdingAccountJoin.get("name"), like));
				searchPredicates.add(likeNullable(cb, sourceHoldingAccountJoin.get("name"), like));
				searchPredicates.add(likeNullable(cb, targetHoldingAccountJoin.get("name"), like));
				searchPredicates.add(likeNullable(cb, categoryJoin.get("name"), like));

				if (!searchCategoryIds.isEmpty()) {
					searchPredicates.add(categoryJoin.get("id").in(searchCategoryIds));
				}

				predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private static Predicate likeNullable(jakarta.persistence.criteria.CriteriaBuilder cb,
	                                      jakarta.persistence.criteria.Expression<String> expression,
	                                      String like) {
		return cb.and(cb.isNotNull(expression), cb.like(cb.lower(expression), like));
	}
}