package org.leoric.expensetracker.expensetracker.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestAuthorizationInfoDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponseDto;
import org.leoric.expensetracker.expensetracker.dto.InviteUserRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ExpenseTrackerAccessRequestService {

	ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestCreate(User currentUser, UUID expenseTrackerId);

	ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestInvite(User currentUser, UUID expenseTrackerId, InviteUserRequestDto request);

	ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestApprove(User currentUser, UUID requestId);

	ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestReject(User currentUser, UUID requestId);

	void expenseTrackerAccessRequestCancel(User currentUser, UUID requestId);

	ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestAccept(User currentUser, UUID requestId);

	ExpenseTrackerAccessRequestAuthorizationInfoDto getAuthorizationInfo(UUID requestId);

	Page<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestFindAllMine(User currentUser, String search, Pageable pageable);

	Page<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestFindAllByTracker(User currentUser, UUID expenseTrackerId, String search, Pageable pageable);
}