package org.leoric.expensetracker.transaction.repositories;

import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	Page<Transaction> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	@Query("""
			SELECT t FROM Transaction t
			WHERE t.expenseTracker.id = :trackerId
			AND (
				LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(t.note) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(t.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(t.transactionType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(t.status AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (t.wallet IS NOT NULL AND LOWER(t.wallet.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (t.sourceWallet IS NOT NULL AND LOWER(t.sourceWallet.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (t.targetWallet IS NOT NULL AND LOWER(t.targetWallet.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (t.category IS NOT NULL AND LOWER(t.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<Transaction> findByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

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