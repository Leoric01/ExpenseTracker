package org.leoric.expensetracker.wallet.repositories;

import org.leoric.expensetracker.wallet.models.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

	List<Wallet> findByExpenseTrackerId(UUID expenseTrackerId);

	List<Wallet> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	Page<Wallet> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	@Query("""
			SELECT w FROM Wallet w
			WHERE w.expenseTracker.id = :trackerId
			AND w.active = true
			AND (
				LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(w.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(w.walletType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Wallet> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID expenseTrackerId, String name);
}