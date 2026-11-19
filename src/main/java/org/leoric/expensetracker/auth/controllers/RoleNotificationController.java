package org.leoric.expensetracker.auth.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.GlobalRoleRequestRepository;
import org.leoric.expensetracker.auth.repositories.RoleChangeAuditRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus.*;
import static org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType.*;

@RestController
@RequestMapping("/api/roles/notifications")
@RequiredArgsConstructor
public class RoleNotificationController {
	private final GlobalRoleRequestRepository globalRequests;
	private final ExpenseTrackerAccessRequestRepository trackerRequests;
	private final RoleChangeAuditRepository audits;
	private final ExpenseTrackerRepository trackers;

	public record Decision(String id, String label, String context, String status, Instant date) {}
	public record Notifications(long globalPending, long trackerPending, List<Decision> decisions) {}

	@GetMapping
	@Transactional(readOnly = true)
	public Notifications get(@AuthenticationPrincipal User user) {
		boolean admin = user.hasGlobalRole(ADMIN);
		var types = List.of(REQUEST);
		long global = admin ? globalRequests.countByStatus(PENDING) : 0;
		long tracker = admin ? trackerRequests.countByExpenseTrackerAccessRequestStatusAndExpenseTrackerAccessRequestTypeIn(PENDING, types)
				: trackerRequests.countPendingForOwner(user.getId(), EXPENSETRACKER_OWNER, PENDING, types);
		List<Decision> decisions = new ArrayList<>();
		globalRequests.findTop5ByUserIdAndStatusInOrderByDecisionDateDesc(user.getId(), List.of(APPROVED, REJECTED))
				.forEach(r -> decisions.add(new Decision("global-" + r.getId(), r.getRoleName(), null, r.getStatus().name(), r.getDecisionDate())));
		trackerRequests.findTop5ByUserIdAndExpenseTrackerAccessRequestStatusInOrderByApprovalDateDesc(user.getId(), List.of(APPROVED, REJECTED))
				.stream().filter(r -> r.getApprovalDate() != null).forEach(r -> decisions.add(new Decision("tracker-" + r.getId(),
						r.getExpenseTracker().getName(), null, r.getExpenseTrackerAccessRequestStatus().name(), r.getApprovalDate())));
		audits.findTop5ByTargetIdOrderByChangedAtDesc(user.getId()).forEach(a -> {
			String context = a.getTrackerId() == null ? null : trackers.findById(a.getTrackerId())
					.map(t -> t.getName()).orElse("smazaný tracker");
			decisions.add(new Decision("audit-" + a.getId(), a.getRoleName(), context, a.getAction(), a.getChangedAt()));
		});
		decisions.sort(Comparator.comparing(Decision::date, Comparator.nullsLast(Comparator.reverseOrder())));
		return new Notifications(global, tracker, decisions);
	}
}
