package org.leoric.expensetracker.transaction.repositories;

import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Page<Transaction> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	List<Transaction> findByExpenseTrackerIdAndTransactionDateBetween(
			UUID expenseTrackerId, LocalDate from, LocalDate to);

	List<Transaction> findByExpenseTrackerIdAndWalletIdAndTransactionDateBetween(
			UUID expenseTrackerId, UUID walletId, LocalDate from, LocalDate to);

	List<Transaction> findByExpenseTrackerIdAndCategoryIdAndTransactionDateBetween(
			UUID expenseTrackerId, UUID categoryId, LocalDate from, LocalDate to);

	List<Transaction> findByExpenseTrackerIdAndTransactionTypeAndTransactionDateBetween(
			UUID expenseTrackerId, TransactionType transactionType, LocalDate from, LocalDate to);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}