package org.leoric.expensetracker.expensetracker.repositories;

import org.leoric.expensetracker.expensetracker.models.ExpenseTrackerAccessRequest;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExpenseTrackerAccessRequestRepository extends JpaRepository<ExpenseTrackerAccessRequest, UUID> {

	Page<ExpenseTrackerAccessRequest> findByUserId(UUID userId, Pageable pageable);

	@Query("SELECT r FROM ExpenseTrackerAccessRequest r WHERE r.user.id = :userId " +
			"AND LOWER(r.expenseTracker.name) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<ExpenseTrackerAccessRequest> findByUserIdAndSearch(@Param("userId") UUID userId,
	                                                        @Param("search") String search,
	                                                        Pageable pageable);

	Page<ExpenseTrackerAccessRequest> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	@Query("SELECT r FROM ExpenseTrackerAccessRequest r WHERE r.expenseTracker.id = :trackerId " +
			"AND LOWER(r.user.email) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<ExpenseTrackerAccessRequest> findByExpenseTrackerIdAndSearch(@Param("trackerId") UUID trackerId,
	                                                                  @Param("search") String search,
	                                                                  Pageable pageable);

	boolean existsByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(
			UUID userId, UUID expenseTrackerId, ExpenseTrackerAccessRequestStatus status);
}