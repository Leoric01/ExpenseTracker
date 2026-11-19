package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestAuthorizationInfoDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponseDto;
import org.leoric.expensetracker.expensetracker.dto.InviteUserRequestDto;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerAccessRequestMapper;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.models.ExpenseTrackerAccessRequest;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessRequestService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.handler.exceptions.NotAuthorizedForThisExpenseTrackerException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;
import static org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus.PENDING;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseTrackerAccessRequestServiceImpl implements ExpenseTrackerAccessRequestService {

	private final ExpenseTrackerAccessRequestRepository accessRequestRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ExpenseTrackerAccessRequestMapper accessRequestMapper;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final TrackerRoleManagementService trackerRoleManagementService;
	private final EntityManager entityManager;

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestFindAll(ExpenseTrackerAccessRequestStatus status, Pageable pageable) {
		return (status == null ? accessRequestRepository.findAll(pageable) : accessRequestRepository.findByExpenseTrackerAccessRequestStatus(status, pageable)).map(accessRequestMapper::toResponse);
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerOwnerRequestCreate(User currentUser, UUID expenseTrackerId) {
		ExpenseTracker tracker = getTrackerOrThrow(expenseTrackerId);
		User user = getUserOrThrow(currentUser.getId());
		if (tracker.getUsers().stream().noneMatch(u -> u.getId().equals(user.getId())))
			throw new OperationNotPermittedException("Only a tracker member can request owner access");
		if (user.hasRoleOnExpenseTracker(EXPENSETRACKER_OWNER, expenseTrackerId))
			throw new OperationNotPermittedException("Owner role is already assigned");
		assertNoPendingRequest(user.getId(), expenseTrackerId);
		return accessRequestMapper.toResponse(accessRequestRepository.save(ExpenseTrackerAccessRequest.builder()
				.user(user).expenseTracker(tracker).requestedRole(EXPENSETRACKER_OWNER)
				.expenseTrackerAccessRequestStatus(PENDING).expenseTrackerAccessRequestType(ExpenseTrackerAccessRequestType.REQUEST)
				.build()));
	}

	@Override
	@Transactional(readOnly = true)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerPendingOwnerRequest(User currentUser, UUID expenseTrackerId) {
		return accessRequestRepository.findByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(currentUser.getId(), expenseTrackerId, PENDING)
				.stream().filter(r -> EXPENSETRACKER_OWNER.equals(r.getRequestedRole()))
				.findFirst().map(accessRequestMapper::toResponse).orElse(null);
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestCreate(User currentUser, UUID expenseTrackerId) {
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
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestInvite(User currentUser, UUID expenseTrackerId, InviteUserRequestDto inviteRequest) {
 		ExpenseTracker tracker = getTrackerOrThrow(expenseTrackerId);
		assertCanManage(currentUser, tracker);
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
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestApprove(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestForUpdate(requestId);
		assertCanManage(currentUser, request.getExpenseTracker());
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() != ExpenseTrackerAccessRequestType.REQUEST) {
			throw new OperationNotPermittedException("Only access requests can be approved by the owner");
		}

		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.APPROVED);
		request.setApprovedBy(currentUser);
		request.setApprovalDate(Instant.now());
		if (EXPENSETRACKER_OWNER.equals(request.getRequestedRole())) {
			User user = request.getUser();
			UUID trackerId = request.getExpenseTracker().getId();
			if (request.getExpenseTracker().getUsers().stream().noneMatch(u -> u.getId().equals(user.getId())))
				throw new OperationNotPermittedException("The user is no longer a tracker member");
			var selected = new HashSet<String>();
			user.getUserExpenseTrackerRoles().stream().filter(a -> a.getExpenseTracker().getId().equals(trackerId))
					.forEach(a -> selected.add(a.getRole().getName()));
			var previous = new HashSet<>(selected);
			selected.add(EXPENSETRACKER_OWNER);
			accessRequestRepository.save(request);
			trackerRoleManagementService.setRoles(currentUser, trackerId, user.getId(), selected, previous);
		} else {
			grantMembership(request);
		}

		request = accessRequestRepository.save(request);
		log.info("Owner {} approved access request for user {} on tracker '{}'",
				currentUser.getEmail(), request.getUser().getEmail(), request.getExpenseTracker().getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestReject(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestForUpdate(requestId);
		if (request.getExpenseTrackerAccessRequestType() != ExpenseTrackerAccessRequestType.INVITE)
			assertCanManage(currentUser, request.getExpenseTracker());
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() == ExpenseTrackerAccessRequestType.INVITE
				&& !request.getUser().getId().equals(currentUser.getId())) {
			throw new OperationNotPermittedException("Only the invited user can reject an invite");
		}

		request.setExpenseTrackerAccessRequestStatus(ExpenseTrackerAccessRequestStatus.REJECTED);
		request.setApprovedBy(currentUser);
		request.setApprovalDate(Instant.now());
		request = accessRequestRepository.save(request);

		log.info("User {} rejected access request {} on tracker '{}'",
				currentUser.getEmail(), requestId, request.getExpenseTracker().getName());
		return accessRequestMapper.toResponse(request);
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void expenseTrackerAccessRequestCancel(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestForUpdate(requestId);
		assertPending(request);

		if (request.getExpenseTrackerAccessRequestType() != ExpenseTrackerAccessRequestType.INVITE) {
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
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ExpenseTrackerAccessRequestResponseDto expenseTrackerAccessRequestAccept(User currentUser, UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestForUpdate(requestId);
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
	public Page<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestFindAllMine(User currentUser, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return accessRequestRepository.findByUserIdAndSearch(currentUser.getId(), search, pageable)
					.map(accessRequestMapper::toResponse);
		}
		return accessRequestRepository.findByUserId(currentUser.getId(), pageable)
				.map(accessRequestMapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestFindAllByTracker(User currentUser, UUID expenseTrackerId, String search, ExpenseTrackerAccessRequestStatus status, Pageable pageable) {
		if (status != null) {
			return accessRequestRepository.findByTrackerAndStatusAndSearch(expenseTrackerId, status, search == null ? "" : search, pageable)
					.map(accessRequestMapper::toResponse);
		}
		if (search != null && !search.isBlank()) {
			return accessRequestRepository.findByExpenseTrackerIdAndSearch(expenseTrackerId, search, pageable)
					.map(accessRequestMapper::toResponse);
		}
		return accessRequestRepository.findByExpenseTrackerId(expenseTrackerId, pageable)
				.map(accessRequestMapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public ExpenseTrackerAccessRequestAuthorizationInfoDto getAuthorizationInfo(UUID requestId) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(requestId);
		return new ExpenseTrackerAccessRequestAuthorizationInfoDto(
				request.getExpenseTracker().getId(), request.getExpenseTrackerAccessRequestType());
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
		return expenseTrackerRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private ExpenseTrackerAccessRequest getRequestOrThrow(UUID id) {
		return accessRequestRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Access request not found"));
	}

	private ExpenseTrackerAccessRequest getRequestForUpdate(UUID id) {
		ExpenseTrackerAccessRequest request = getRequestOrThrow(id);
		ExpenseTracker tracker = getTrackerOrThrow(request.getExpenseTracker().getId());
		entityManager.refresh(tracker);
		entityManager.refresh(request);
		return request;
	}

	private void assertCanManage(User actor, ExpenseTracker tracker) {
		User refreshed = getUserOrThrow(actor.getId());
		boolean owner = tracker.getUserExpenseTrackerRoles().stream().anyMatch(a ->
				a.getUser().getId().equals(actor.getId()) && EXPENSETRACKER_OWNER.equals(a.getRole().getName()));
		if (!refreshed.hasGlobalRole(ADMIN) && !owner) throw new NotAuthorizedForThisExpenseTrackerException();
	}
}