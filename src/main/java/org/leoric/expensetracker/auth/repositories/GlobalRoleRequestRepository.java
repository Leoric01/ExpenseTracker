package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.GlobalRoleRequest;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface GlobalRoleRequestRepository extends JpaRepository<GlobalRoleRequest, UUID> {
	Page<GlobalRoleRequest> findByUserId(UUID userId, Pageable pageable);
	Page<GlobalRoleRequest> findByStatus(ExpenseTrackerAccessRequestStatus status, Pageable pageable);
	boolean existsByUserIdAndRoleNameAndStatus(UUID userId, String roleName, ExpenseTrackerAccessRequestStatus status);
	List<GlobalRoleRequest> findByUserIdAndStatus(UUID userId, ExpenseTrackerAccessRequestStatus status);
	long countByStatus(ExpenseTrackerAccessRequestStatus status);
	List<GlobalRoleRequest> findTop5ByUserIdAndStatusInOrderByDecisionDateDesc(UUID userId, Collection<ExpenseTrackerAccessRequestStatus> statuses);
	void deleteByUserId(UUID userId);
	@Modifying
	@Query("UPDATE GlobalRoleRequest r SET r.decidedBy = NULL WHERE r.decidedBy.id = :userId")
	void nullifyDecidedBy(@Param("userId") UUID userId);
}
