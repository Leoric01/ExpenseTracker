package org.leoric.expensetracker.transaction.repositories;

import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Page<Transaction> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	List<Transaction> findByExpenseTrackerIdAndTransactionDateBetween(
			UUID expenseTrackerId, Instant from, Instant to);

	List<Transaction> findByExpenseTrackerIdAndWalletIdAndTransactionDateBetween(
			UUID expenseTrackerId, UUID walletId, Instant from, Instant to);

	List<Transaction> findByExpenseTrackerIdAndCategoryIdAndTransactionDateBetween(
			UUID expenseTrackerId, UUID categoryId, Instant from, Instant to);

	List<Transaction> findByExpenseTrackerIdAndTransactionTypeAndTransactionDateBetween(
			UUID expenseTrackerId, TransactionType transactionType, Instant from, Instant to);

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
}