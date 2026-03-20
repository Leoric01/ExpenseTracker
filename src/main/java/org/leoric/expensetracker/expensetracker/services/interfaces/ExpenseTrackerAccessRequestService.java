package org.leoric.expensetracker.expensetracker.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponse;
import org.leoric.expensetracker.expensetracker.dto.InviteUserRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ExpenseTrackerAccessRequestService {

	ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestCreate(User currentUser, UUID expenseTrackerId);

	ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestInvite(User currentUser, UUID expenseTrackerId, InviteUserRequest request);

	ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestApprove(User currentUser, UUID requestId);

	ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestReject(User currentUser, UUID requestId);

	void expenseTrackerAccessRequestCancel(User currentUser, UUID requestId);

	ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestAccept(User currentUser, UUID requestId);

	Page<ExpenseTrackerAccessRequestResponse> expenseTrackerAccessRequestFindAllMine(User currentUser, String search, Pageable pageable);

	Page<ExpenseTrackerAccessRequestResponse> expenseTrackerAccessRequestFindAllByTracker(User currentUser, UUID expenseTrackerId, String search, Pageable pageable);
}