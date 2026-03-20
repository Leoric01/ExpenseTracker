package org.leoric.expensetracker.expensetracker.repositories;

import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExpenseTrackerRepository extends JpaRepository<ExpenseTracker, UUID> {

	Page<ExpenseTracker> findByUsersIdAndActiveTrue(UUID userId, Pageable pageable);

	Page<ExpenseTracker> findByUsersIdAndActiveTrueAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);

	boolean existsByCreatedByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);
}