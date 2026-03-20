package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserExpenseTrackerRoleRepository extends JpaRepository<UserExpenseTrackerRole, Integer> {
}