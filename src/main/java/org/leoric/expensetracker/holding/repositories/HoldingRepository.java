package org.leoric.expensetracker.holding.repositories;

import org.leoric.expensetracker.holding.models.Holding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, UUID> {

	@Query("""
			SELECT h FROM Holding h
			WHERE h.account.institution.expenseTracker.id = :trackerId
			AND h.active = true
			""")
	List<Holding> findByExpenseTrackerIdAndActiveTrue(@Param("trackerId") UUID trackerId);

	@Query("""
			SELECT h FROM Holding h
			WHERE h.account.institution.expenseTracker.id = :trackerId
			AND h.active = true
			AND h.account.active = true
			AND h.account.institution.active = true
			""")
	List<Holding> findByExpenseTrackerIdAndFullyActiveHierarchy(@Param("trackerId") UUID trackerId);

	@Query("""
			SELECT h FROM Holding h
			WHERE h.account.institution.expenseTracker.id = :trackerId
			AND h.active = true
			""")
	Page<Holding> findByExpenseTrackerIdAndActiveTrue(@Param("trackerId") UUID trackerId, Pageable pageable);

	@Query("""
			SELECT h FROM Holding h
			WHERE h.account.institution.expenseTracker.id = :trackerId
			AND h.active = true
			AND (
				LOWER(h.asset.code) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(h.asset.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(h.account.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(h.account.institution.name) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Holding> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	List<Holding> findByAccountIdAndActiveTrue(UUID accountId);

	boolean existsByAccountIdAndAssetId(UUID accountId, UUID assetId);

	@Modifying
	@Query("DELETE FROM Holding h WHERE h.account.institution.expenseTracker.id = :trackerId")
	void deleteByExpenseTrackerId(@Param("trackerId") UUID trackerId);
}