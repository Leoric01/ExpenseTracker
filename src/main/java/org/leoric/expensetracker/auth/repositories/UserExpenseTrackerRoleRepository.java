package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserExpenseTrackerRoleRepository extends JpaRepository<UserExpenseTrackerRole, Integer> {

	boolean existsByUserIdAndExpenseTrackerIdAndRoleName(UUID userId, UUID expenseTrackerId, String roleName);
}