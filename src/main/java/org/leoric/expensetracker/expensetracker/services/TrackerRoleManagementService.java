package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.RoleChangeAudit;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleChangeAuditRepository;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.handler.exceptions.NotAuthorizedForThisExpenseTrackerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;

@Service
@RequiredArgsConstructor
public class TrackerRoleManagementService {
	private static final Set<String> TRACKER_ROLES = Set.of(EXPENSETRACKER_OWNER, EXPENSETRACKER_MEMBER);
	private final ExpenseTrackerRepository trackers;
	private final UserRepository users;
	private final RoleRepository roles;
	private final RoleChangeAuditRepository audits;
	private final ExpenseTrackerAccessRequestRepository requests;

	@Transactional
	public void assertCanDeleteUser(User user) {
		var ids = user.getUserExpenseTrackerRoles().stream()
				.filter(a -> EXPENSETRACKER_OWNER.equals(a.getRole().getName()))
				.map(a -> a.getExpenseTracker().getId()).distinct().sorted().toList();
		for (UUID id : ids) {
			ExpenseTracker tracker = trackers.findByIdForUpdate(id)
					.orElseThrow(() -> new EntityNotFoundException("Tracker not found"));
			if (tracker.getCreatedByOwner().getId().equals(user.getId())) continue;
			long owners = tracker.getUserExpenseTrackerRoles().stream()
					.filter(a -> EXPENSETRACKER_OWNER.equals(a.getRole().getName()))
					.map(a -> a.getUser().getId()).distinct().count();
			if (owners <= 1) throw new OperationNotPermittedException("The last owner cannot be deleted");
		}
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void setRoles(User actor, UUID trackerId, UUID targetId, Set<String> selection, Set<String> expectedRoles) {
		if (selection == null || !TRACKER_ROLES.containsAll(selection))
			throw new OperationNotPermittedException("Invalid tracker role");
		ExpenseTracker tracker = trackers.findByIdForUpdate(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Tracker not found"));
		User currentActor = users.findById(actor.getId()).orElseThrow(() -> new EntityNotFoundException("User not found"));
		boolean owner = tracker.getUserExpenseTrackerRoles().stream().anyMatch(a ->
				a.getUser().getId().equals(actor.getId()) && EXPENSETRACKER_OWNER.equals(a.getRole().getName()));
		if (!currentActor.hasGlobalRole(ADMIN) && !owner) throw new NotAuthorizedForThisExpenseTrackerException();
		User target = users.findById(targetId).orElseThrow(() -> new EntityNotFoundException("User not found"));
		Set<String> previous = new HashSet<>();
		for (UserExpenseTrackerRole assignment : tracker.getUserExpenseTrackerRoles()) {
			if (assignment.getUser().getId().equals(targetId)) previous.add(assignment.getRole().getName());
		}
		if (!previous.equals(expectedRoles)) throw new OperationNotPermittedException("Roles changed; reload before saving");
		if (!currentActor.hasGlobalRole(ADMIN) && previous.isEmpty())
			throw new OperationNotPermittedException("Owners can manage only existing tracker members");
		if (previous.contains(EXPENSETRACKER_OWNER) && !selection.contains(EXPENSETRACKER_OWNER)) {
			if (tracker.getCreatedByOwner().getId().equals(targetId))
				throw new OperationNotPermittedException("The tracker creator must remain an owner");
			long owners = tracker.getUserExpenseTrackerRoles().stream()
					.filter(a -> EXPENSETRACKER_OWNER.equals(a.getRole().getName())).map(a -> a.getUser().getId()).distinct().count();
			if (owners <= 1) throw new OperationNotPermittedException("The last owner cannot be removed");
		}

		for (UserExpenseTrackerRole assignment : new ArrayList<>(tracker.getUserExpenseTrackerRoles())) {
			if (assignment.getUser().getId().equals(targetId) && !selection.contains(assignment.getRole().getName())) {
				tracker.getUserExpenseTrackerRoles().remove(assignment);
				target.getUserExpenseTrackerRoles().remove(assignment);
				audit(actor, target, trackerId, assignment.getRole().getName(), "REMOVED");
			}
		}
		for (String name : selection) {
			if (!previous.contains(name)) {
				Role role = roles.findByName(name).orElseThrow(() -> new EntityNotFoundException("Role not found: " + name));
				UserExpenseTrackerRole assignment = UserExpenseTrackerRole.builder().user(target).expenseTracker(tracker).role(role).build();
				tracker.getUserExpenseTrackerRoles().add(assignment);
				target.getUserExpenseTrackerRoles().add(assignment);
				audit(actor, target, trackerId, name, "GRANTED");
			}
		}
		if (selection.isEmpty()) {
			target.getExpenseTrackers().removeIf(t -> t.getId().equals(trackerId));
			tracker.getUsers().removeIf(u -> u.getId().equals(targetId));
		} else if (target.getExpenseTrackers().stream().noneMatch(t -> t.getId().equals(trackerId))) {
			target.getExpenseTrackers().add(tracker);
			tracker.getUsers().add(target);
		}
		users.save(target);
		trackers.save(tracker);
		for (var request : requests.findByUserIdAndExpenseTrackerIdAndExpenseTrackerAccessRequestStatus(targetId, trackerId,
				ExpenseTrackerAccessRequestStatus.PENDING)) {
			boolean ownerRequested = EXPENSETRACKER_OWNER.equals(request.getRequestedRole());
			boolean ownerGranted = ownerRequested
					&& selection.contains(EXPENSETRACKER_OWNER);
			boolean membershipChanged = !ownerRequested
					&& !selection.isEmpty();
			if (ownerGranted || membershipChanged || selection.isEmpty()) {
				boolean accessGranted = membershipChanged && request.getExpenseTrackerAccessRequestType() == ExpenseTrackerAccessRequestType.REQUEST;
				request.setExpenseTrackerAccessRequestStatus(ownerGranted || accessGranted ? ExpenseTrackerAccessRequestStatus.APPROVED : ExpenseTrackerAccessRequestStatus.CANCELLED);
				request.setApprovedBy(actor);
				request.setApprovalDate(Instant.now());
				requests.save(request);
			}
		}
	}

	private void audit(User actor, User target, UUID trackerId, String role, String action) {
		audits.save(RoleChangeAudit.builder().actorId(actor.getId()).targetId(target.getId()).trackerId(trackerId)
				.roleName(role).action(action).changedAt(Instant.now()).build());
	}
}
