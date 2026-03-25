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
			AND (t.wallet.id = :walletId OR t.sourceWallet.id = :walletId OR t.targetWallet.id = :walletId)
			""")
	List<Transaction> findCompletedByWalletAndDateRange(
			@Param("walletId") UUID walletId,
			@Param("from") Instant from,
			@Param("to") Instant to);

	@Query("""
			SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
			WHERE t.status = org.leoric.expensetracker.transaction.models.constants.TransactionStatus.COMPLETED
			AND t.expenseTracker.id = :trackerId
			AND t.transactionType = :transactionType
			AND t.category.id IN :categoryIds
			AND t.transactionDate >= :from
			AND t.transactionDate < :to
			""")
	long sumAmountByCategoryIdsAndDateRange(
			@Param("trackerId") UUID trackerId,
			@Param("transactionType") TransactionType transactionType,
			@Param("categoryIds") Set<UUID> categoryIds,
			@Param("from") Instant from,
			@Param("to") Instant to);
}