package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponse;
import org.leoric.expensetracker.expensetracker.dto.InviteUserRequest;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerAccessRequestMapper;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.models.ExpenseTrackerAccessRequest;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessRequestService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus.PENDING;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseTrackerAccessRequestServiceImpl implements ExpenseTrackerAccessRequestService {

	private final ExpenseTrackerAccessRequestRepository accessRequestRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ExpenseTrackerAccessRequestMapper accessRequestMapper;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	@Override
	@Transactional
	public ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestCreate(User currentUser, UUID expenseTrackerId) {
		currentUser = getUserOrThrow(currentUser.getId());
		ExpenseTracker tracker = getTrackerOrThrow(expenseTrackerId);

		assertNotAlreadyMember(currentUser, tracker);
		assertNoPendingRequest(currentUser.getId(), expenseTrackerId);

		ExpenseTrackerAccessRequest request = ExpenseTrackerAccessRequest.builder()
				.user(currentUser)
				.expenseTracker(tracker)
				.expenseTrackerAccessRequestStatus(PENDING)
				.expenseTrackerAccessRequestType(ExpenseTrackerAccessRequestType.REQUEST)
				.build();

		request = accessRequestRepository.save(request);
		log.info("User {} requested access to tracker '{}'", currentUser.getEmail(), tracker.getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional
	public ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestInvite(User currentUser, UUID expenseTrackerId, InviteUserRequest inviteRequest) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(expenseTrackerId, currentUser, EXPENSETRACKER_OWNER);

		ExpenseTracker tracker = getTrackerOrThrow(expenseTrackerId);
		User invitedUser = userRepository.findByEmail(inviteRequest.email())
				.orElseThrow(() -> new EntityNotFoundException("User with email " + inviteRequest.email() + " not found"));

		if (invitedUser.getId().equals(currentUser.getId())) {
			throw new OperationNotPermittedException("You cannot invite yourself");
		}

		assertNotAlreadyMember(invitedUser, tracker);
		assertNoPendingRequest(invitedUser.getId(), expenseTrackerId);

		ExpenseTrackerAccessRequest request = ExpenseTrackerAccessRequest.builder()
				.user(invitedUser)
				.expenseTracker(tracker)
				.expenseTrackerAccessRequestStatus(PENDING)
				.expenseTrackerAccessRequestType(ExpenseTrackerAccessRequestType.INVITE)
				.invitedBy(currentUser)
				.build();

		request = accessRequestRepository.save(request);
		log.info("User {} invited {} to tracker '{}'", currentUser.getEmail(), invitedUser.getEmail(), tracker.getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional
	public ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestApprove(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(requestId);
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() != ExpenseTrackerAccessRequestType.REQUEST) {
			throw new OperationNotPermittedException("Only access requests can be approved by the owner");
		}

		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				request.getExpenseTracker().getId(), currentUser, EXPENSETRACKER_OWNER);

		grantMembership(request);
		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.APPROVED);
		request.setApprovedBy(currentUser);
		request.setApprovalDate(Instant.now());

		request = accessRequestRepository.save(request);
		log.info("Owner {} approved access request for user {} on tracker '{}'",
				currentUser.getEmail(), request.getUser().getEmail(), request.getExpenseTracker().getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional
	public ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestReject(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(requestId);
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() == ExpenseTrackerAccessRequestType.REQUEST) {
			expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
					request.getExpenseTracker().getId(), currentUser, EXPENSETRACKER_OWNER);
		} else {
			if (!request.getUser().getId().equals(currentUser.getId())) {
				throw new OperationNotPermittedException("Only the invited user can reject an invite");
			}
		}

		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.REJECTED);
		request = accessRequestRepository.save(request);

		log.info("User {} rejected access request {} on tracker '{}'",
				currentUser.getEmail(), requestId, request.getExpenseTracker().getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional
	public void expenseTrackerAccessRequestCancel(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(requestId);
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() == ExpenseTrackerAccessRequestType.REQUEST) {
			if (!request.getUser().getId().equals(currentUser.getId())) {
				throw new OperationNotPermittedException("Only the requestor can cancel their own request");
			}
		} else {
			if (!request.getInvitedBy().getId().equals(currentUser.getId())) {
				throw new OperationNotPermittedException("Only the inviter can cancel their own invite");
			}
		}

		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.CANCELLED);
		accessRequestRepository.save(request);

		log.info("User {} cancelled access request {} on tracker '{}'",
				currentUser.getEmail(), requestId, request.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public ExpenseTrackerAccessRequestResponse expenseTrackerAccessRequestAccept(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(requestId);
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() != ExpenseTrackerAccessRequestType.INVITE) {
			throw new OperationNotPermittedException("Only invites can be accepted");
		}

		if (!request.getUser().getId().equals(currentUser.getId())) {
			throw new OperationNotPermittedException("Only the invited user can accept the invite");
		}

		grantMembership(request);
		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.APPROVED);
		request.setApprovalDate(Instant.now());

		request = accessRequestRepository.save(request);
		log.info("User {} accepted invite to tracker '{}'",
				currentUser.getEmail(), request.getExpenseTracker().getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerAccessRequestResponse> expenseTrackerAccessRequestFindAllMine(User currentUser, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return accessRequestRepository.findByUserIdAndSearch(currentUser.getId(), search, pageable)
					.map(accessRequestMapper::toResponse);
		}
		return accessRequestRepository.findByUserId(currentUser.getId(), pageable)
				.map(accessRequestMapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerAccessRequestResponse> expenseTrackerAccessRequestFindAllByTracker(User currentUser, UUID expenseTrackerId, String search, Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(expenseTrackerId, currentUser, EXPENSETRACKER_OWNER);

		if (search != null && !search.isBlank()) {
			return accessRequestRepository.findByExpenseTrackerIdAndSearch(expenseTrackerId, search, pageable)
					.map(accessRequestMapper::toResponse);
		}
		return accessRequestRepository.findByExpenseTrackerId(expenseTrackerId, pageable)
				.map(accessRequestMapper::toResponse);
	}

	// --- helpers ---

	private void grantMembership(ExpenseTrackerAccessRequest request) {
		User user = request.getUser();
		ExpenseTracker tracker = request.getExpenseTracker();

		Role memberRole = roleRepository.findByName(EXPENSETRACKER_MEMBER)
				.orElseThrow(() -> new EntityNotFoundException("Role " + EXPENSETRACKER_MEMBER + " not found"));

		user.getExpenseTrackers().add(tracker);
		tracker.getUsers().add(user);

		UserExpenseTrackerRole roleAssignment = UserExpenseTrackerRole.builder()
				.user(user)
				.expenseTracker(tracker)
				.role(memberRole)
				.build();

		tracker.getUserExpenseTrackerRoles().add(roleAssignment);
		expenseTrackerRepository.save(tracker);
	}

	private void assertNotAlreadyMember(User user, ExpenseTracker tracker) {
		boolean isMember = tracker.getUsers().stream()
				.anyMatch(u -> u.getId().equals(user.getId()));
		if (isMember) {
			throw new OperationNotPermittedException("User is already a member of this expense tracker");
		}
	}

	private void assertNoPendingRequest(UUID userId, UUID expenseTrackerId) {
		if (accessRequestRepository.existsByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(
				userId, expenseTrackerId, PENDING)) {
			throw new OperationNotPermittedException("There is already a pending request for this expense tracker");
		}
	}

	private void assertPending(ExpenseTrackerAccessRequest request) {
		if (request.getExpenseTrackerAccessRequestStatus() != PENDING) {
			throw new OperationNotPermittedException("This request has already been resolved");
		}
	}

	private User getUserOrThrow(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("User not found"));
	}

	private ExpenseTracker getTrackerOrThrow(UUID id) {
		return expenseTrackerRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private ExpenseTrackerAccessRequest getRequestOrThrow(UUID id) {
		return accessRequestRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Access request not found"));
	}
}