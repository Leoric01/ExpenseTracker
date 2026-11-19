package org.leoric.expensetracker.expensetracker.repositories;

import org.leoric.expensetracker.expensetracker.models.ExpenseTrackerAccessRequest;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;
import java.util.Collection;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;

@Repository
public interface ExpenseTrackerAccessRequestRepository extends JpaRepository<ExpenseTrackerAccessRequest, UUID> {

	Page<ExpenseTrackerAccessRequest> findByUserId(UUID userId, Pageable pageable);

	@Query("SELECT r FROM ExpenseTrackerAccessRequest r WHERE r.user.id = :userId " +
			"AND LOWER(r.expenseTracker.name) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<ExpenseTrackerAccessRequest> findByUserIdAndSearch(@Param("userId") UUID userId,
	                                                        @Param("search") String search,
	                                                        Pageable pageable);

	Page<ExpenseTrackerAccessRequest> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);
	Page<ExpenseTrackerAccessRequest> findByExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus status, Pageable pageable);
	@Query("SELECT r FROM ExpenseTrackerAccessRequest r WHERE r.expenseTracker.id = :trackerId " +
			"AND (:status IS NULL OR r.expenseTrackerAccessRequestStatus = :status) " +
			"AND LOWER(r.user.email) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<ExpenseTrackerAccessRequest> findByTrackerAndStatusAndSearch(@Param("trackerId") UUID trackerId,
			@Param("status") ExpenseTrackerAccessRequestStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT r FROM ExpenseTrackerAccessRequest r WHERE r.expenseTracker.id = :trackerId " +
			"AND LOWER(r.user.email) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<ExpenseTrackerAccessRequest> findByExpenseTrackerIdAndSearch(@Param("trackerId") UUID trackerId,
	                                                                  @Param("search") String search,
	                                                                  Pageable pageable);

	boolean existsByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(
			UUID userId, UUID expenseTrackerId, ExpenseTrackerAccessRequestStatus status);

	List<ExpenseTrackerAccessRequest> findByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(
			UUID userId, UUID trackerId, ExpenseTrackerAccessRequestStatus status);

	long countByExpenseTrackerAccessRequestStatusAndExpenseTrackerAccessRequestTypeIn(
			ExpenseTrackerAccessRequestStatus status, Collection<ExpenseTrackerAccessRequestType> types);

	List<ExpenseTrackerAccessRequest> findTop5ByUserIdAndExpenseTrackerAccessRequestStatusInOrderByApprovalDateDesc(
			UUID userId, Collection<ExpenseTrackerAccessRequestStatus> statuses);

	@Query("""
		SELECT COUNT(r) FROM ExpenseTrackerAccessRequest r
		WHERE r.expenseTrackerAccessRequestStatus = :status
		  AND r.expenseTrackerAccessRequestType IN :types
		  AND EXISTS (SELECT a.id FROM UserExpenseTrackerRole a WHERE a.expenseTracker = r.expenseTracker
		      AND a.user.id = :userId AND a.role.name = :ownerRole)
		""")
	long countPendingForOwner(@Param("userId") UUID userId, @Param("ownerRole") String ownerRole,
			@Param("status") ExpenseTrackerAccessRequestStatus status, @Param("types") Collection<ExpenseTrackerAccessRequestType> types);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	void deleteByUserId(UUID userId);

	@Modifying
	@Query("UPDATE ExpenseTrackerAccessRequest r SET r.approvedBy = NULL WHERE r.approvedBy.id = :userId")
	void nullifyApprovedByUserId(@Param("userId") UUID userId);

	@Modifying
	@Query("UPDATE ExpenseTrackerAccessRequest r SET r.invitedBy = NULL WHERE r.invitedBy.id = :userId")
	void nullifyInvitedByUserId(@Param("userId") UUID userId);
}