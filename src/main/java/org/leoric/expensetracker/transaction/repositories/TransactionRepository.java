package org.leoric.expensetracker.transaction.repositories;

import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	@Query("""
			SELECT t FROM Transaction t
			WHERE t.status = org.leoric.expensetracker.transaction.models.constants.TransactionStatus.COMPLETED
			AND t.transactionDate >= :from
			AND t.transactionDate < :to
			AND (t.holding.id = :holdingId OR t.sourceHolding.id = :holdingId OR t.targetHolding.id = :holdingId)
			""")
	List<Transaction> findCompletedByHoldingAndDateRange(
			@Param("holdingId") UUID holdingId,
			@Param("from") Instant from,
			@Param("to") Instant to);

	@Query("""
			SELECT t FROM Transaction t
			WHERE t.status = org.leoric.expensetracker.transaction.models.constants.TransactionStatus.COMPLETED
			AND t.expenseTracker.id = :trackerId
			AND t.transactionType = :transactionType
			AND t.category.id IN :categoryIds
			AND t.transactionDate >= :from
			AND t.transactionDate < :to
			""")
	List<Transaction> findCompletedByCategoryIdsAndDateRange(
			@Param("trackerId") UUID trackerId,
			@Param("transactionType") TransactionType transactionType,
			@Param("categoryIds") Set<UUID> categoryIds,
			@Param("from") Instant from,
			@Param("to") Instant to);

	@Query("""
			SELECT t FROM Transaction t
			LEFT JOIN FETCH t.category
			LEFT JOIN FETCH t.sourceHolding sh
			LEFT JOIN FETCH sh.asset
			LEFT JOIN FETCH t.targetHolding th
			LEFT JOIN FETCH th.asset
			WHERE t.status = org.leoric.expensetracker.transaction.models.constants.TransactionStatus.COMPLETED
			AND t.expenseTracker.id = :trackerId
			AND t.transactionDate >= :from
			AND t.transactionDate <= :to
			""")
	List<Transaction> findCompletedByExpenseTrackerIdAndDateRange(
			@Param("trackerId") UUID trackerId,
			@Param("from") Instant from,
			@Param("to") Instant to);
}