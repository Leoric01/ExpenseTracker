package org.leoric.expensetracker.budget.repositories;

import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, UUID> {

	List<BudgetPlan> findByExpenseTrackerId(UUID expenseTrackerId);

	List<BudgetPlan> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);
}