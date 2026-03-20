package org.leoric.expensetracker.wallet.repositories;

import org.leoric.expensetracker.wallet.models.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

	List<Wallet> findByExpenseTrackerId(UUID expenseTrackerId);

	List<Wallet> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}