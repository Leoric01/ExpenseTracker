package org.leoric.expensetracker.expensetracker.repositories;

import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseTrackerRepository extends JpaRepository<ExpenseTracker, UUID> {

	Page<ExpenseTracker> findByUsersIdAndActiveTrue(UUID userId, Pageable pageable);

	Page<ExpenseTracker> findByUsersIdAndActiveTrueAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);

	boolean existsByCreatedByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

	List<ExpenseTracker> findByCreatedByOwnerId(UUID ownerId);

	@Query("""
			SELECT et FROM ExpenseTracker et
			WHERE et.active = true
			AND et.id NOT IN (
				SELECT et2.id FROM ExpenseTracker et2
				JOIN et2.users u
				WHERE u.id = :userId
			)
			""")
	Page<ExpenseTracker> findAllNotMine(@Param("userId") UUID userId, Pageable pageable);

	@Query("""
			SELECT et FROM ExpenseTracker et
			WHERE et.active = true
			AND et.id NOT IN (
				SELECT et2.id FROM ExpenseTracker et2
				JOIN et2.users u
				WHERE u.id = :userId
			)
			AND (
				LOWER(et.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CONCAT(et.createdByOwner.firstName, ' ', et.createdByOwner.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(et.preferredDisplayAsset.code) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(et.description) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<ExpenseTracker> findAllNotMineWithSearch(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);
}