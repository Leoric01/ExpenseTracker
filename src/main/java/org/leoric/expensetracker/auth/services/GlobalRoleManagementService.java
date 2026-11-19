package org.leoric.expensetracker.auth.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.RoleManagementDtos.GlobalRequestResponse;
import org.leoric.expensetracker.auth.models.GlobalRoleRequest;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.RoleChangeAudit;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.GlobalRoleRequestRepository;
import org.leoric.expensetracker.auth.repositories.RoleChangeAuditRepository;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;
import static org.leoric.expensetracker.ExpenseTrackerApplication.USER;
import static org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus.*;

@Service
@RequiredArgsConstructor
public class GlobalRoleManagementService {
	private static final Set<String> GLOBAL_ROLES = Set.of(USER, ADMIN);
	private final UserRepository users;
	private final RoleRepository roles;
	private final GlobalRoleRequestRepository requests;
	private final RoleChangeAuditRepository audits;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public Page<GlobalRequestResponse> mine(User currentUser, Pageable pageable) {
		return requests.findByUserId(currentUser.getId(), pageable).map(GlobalRequestResponse::from);
	}

	@Transactional(readOnly = true)
	public List<GlobalRequestResponse> minePending(User currentUser) {
		return requests.findByUserIdAndStatus(currentUser.getId(), PENDING).stream().map(GlobalRequestResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public Page<GlobalRequestResponse> all(ExpenseTrackerAccessRequestStatus status, Pageable pageable) {
		return (status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable)).map(GlobalRequestResponse::from);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GlobalRequestResponse request(User currentUser, String roleName) {
		lockGlobalRoles();
		checkRole(roleName);
		User user = getUser(currentUser.getId());
		if (user.hasGlobalRole(roleName)) throw new OperationNotPermittedException("Role already assigned");
		if (requests.existsByUserIdAndRoleNameAndStatus(user.getId(), roleName, PENDING))
			throw new OperationNotPermittedException("Role request is already pending");
		return GlobalRequestResponse.from(requests.save(GlobalRoleRequest.builder()
				.user(user).roleName(roleName).status(PENDING).requestDate(Instant.now()).build()));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GlobalRequestResponse cancel(User actor, UUID requestId) {
		lockGlobalRoles();
		GlobalRoleRequest request = getRequest(requestId);
		if (!request.getUser().getId().equals(actor.getId()))
			throw new OperationNotPermittedException("Only the requestor can cancel this request");
		requirePending(request);
		decide(request, CANCELLED, actor);
		return GlobalRequestResponse.from(request);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GlobalRequestResponse resolve(User actor, UUID requestId, boolean approve) {
		lockGlobalRoles();
		requireAdmin(actor);
		GlobalRoleRequest request = getRequest(requestId);
		requirePending(request);
		if (approve) {
			User target = request.getUser();
			if (target.hasGlobalRole(request.getRoleName()))
				throw new OperationNotPermittedException("Role is already assigned");
			Set<String> selection = new java.util.HashSet<>(target.getRoles().stream().map(Role::getName).toList());
			selection.add(request.getRoleName());
			decide(request, APPROVED, actor);
			replaceRoles(actor, target, selection);
		} else {
			decide(request, REJECTED, actor);
		}
		return GlobalRequestResponse.from(request);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void setRoles(User actor, UUID userId, Set<String> selection, Set<String> expectedRoles) {
		lockGlobalRoles();
		requireAdmin(actor);
		User target = getUser(userId);
		Set<String> current = Set.copyOf(target.getRoles().stream().map(Role::getName).toList());
		if (!current.equals(expectedRoles)) throw new OperationNotPermittedException("Roles changed; reload before saving");
		replaceRoles(actor, target, selection);
	}

	@Transactional
	public void beforeDelete(User user) {
		lockGlobalRoles();
		entityManager.refresh(user);
		if (user.hasGlobalRole(ADMIN) && users.countUsersWithRole(ADMIN) <= 1)
			throw new OperationNotPermittedException("The last administrator cannot be deleted");
		requests.nullifyDecidedBy(user.getId());
		requests.deleteByUserId(user.getId());
	}

	private void replaceRoles(User actor, User target, Set<String> selection) {
		if (selection == null || selection.isEmpty()) throw new OperationNotPermittedException("At least one global role is required");
		selection.forEach(this::checkRole);
		Set<String> previous = Set.copyOf(target.getRoles().stream().map(Role::getName).toList());
		if (previous.contains(ADMIN) && !selection.contains(ADMIN) && users.countUsersWithRole(ADMIN) <= 1)
			throw new OperationNotPermittedException("The last administrator cannot be removed");
		List<Role> newRoles = new ArrayList<>();
		for (String name : selection) newRoles.add(roles.findByName(name).orElseThrow(() -> new EntityNotFoundException("Role not found: " + name)));
		target.setRoles(newRoles);
		users.save(target);
		for (String role : selection) if (!previous.contains(role)) audit(actor, target, role, "GRANTED");
		for (String role : previous) if (!selection.contains(role)) audit(actor, target, role, "REMOVED");
		for (GlobalRoleRequest pending : requests.findByUserIdAndStatus(target.getId(), PENDING)) {
			if (selection.contains(pending.getRoleName())) decide(pending, APPROVED, actor);
		}
	}

	private void audit(User actor, User target, String role, String action) {
		audits.save(RoleChangeAudit.builder().actorId(actor.getId()).targetId(target.getId())
				.roleName(role).action(action).changedAt(Instant.now()).build());
	}

	private void checkRole(String role) {
		if (!GLOBAL_ROLES.contains(role)) throw new OperationNotPermittedException("Invalid global role");
	}

	private void lockGlobalRoles() {
		roles.findByNameForUpdate(ADMIN).orElseThrow(() -> new EntityNotFoundException("ADMIN role not found"));
	}

	private void requireAdmin(User actor) {
		if (!getUser(actor.getId()).hasGlobalRole(ADMIN)) throw new AccessDeniedException("Administrator access required");
	}

	private User getUser(UUID id) {
		return users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
	}

	private GlobalRoleRequest getRequest(UUID id) {
		return requests.findById(id).orElseThrow(() -> new EntityNotFoundException("Role request not found"));
	}

	private void requirePending(GlobalRoleRequest request) {
		if (request.getStatus() != PENDING) throw new OperationNotPermittedException("Request has already been resolved");
	}

	private void decide(GlobalRoleRequest request, ExpenseTrackerAccessRequestStatus status, User actor) {
		request.setStatus(status);
		request.setDecisionDate(Instant.now());
		request.setDecidedBy(actor);
		requests.save(request);
	}
}
