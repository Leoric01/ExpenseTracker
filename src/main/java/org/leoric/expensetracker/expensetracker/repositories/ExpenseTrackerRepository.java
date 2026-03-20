package org.leoric.expensetracker.expensetracker.repositories;

import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExpenseTrackerRepository extends JpaRepository<ExpenseTracker, UUID> {
}