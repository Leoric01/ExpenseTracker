package org.leoric.expensetracker.expensetracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.RoleManagementDtos.TrackerRoleSelection;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMemberDto;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerMapper;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.services.TrackerRoleManagementService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/roles/trackers")
@RequiredArgsConstructor
public class TrackerRoleController {
	private final ExpenseTrackerRepository trackers;
	private final ExpenseTrackerMapper mapper;
	private final ExpenseTrackerAccessService access;
	private final TrackerRoleManagementService service;
	private final UserRepository users;

	public record TrackerSummary(UUID id, String name, boolean active, UUID creatorId, String creatorName) {}

	@GetMapping
	@Secured("ADMIN")
	@Transactional(readOnly = true)
	public Page<TrackerSummary> all(@RequestParam(required = false) String search, @ParameterObject Pageable pageable) {
		var result = search == null || search.isBlank() ? trackers.findAll(pageable) : trackers.findByNameContainingIgnoreCase(search, pageable);
		return result.map(t -> new TrackerSummary(t.getId(), t.getName(), t.isActive(),
				t.getCreatedByOwner().getId(), t.getCreatedByOwner().getFullname()));
	}

	@GetMapping("/{id}/members")
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerMemberDto> memberPage(@AuthenticationPrincipal User user, @PathVariable UUID id,
			@ParameterObject Pageable pageable) {
		access.assertHasRoleOnExpenseTracker(id, user, EXPENSETRACKER_OWNER);
		return users.findByExpenseTrackersId(id, pageable).map(member -> {
			List<org.leoric.expensetracker.auth.models.UserExpenseTrackerRole> assignments = member.getUserExpenseTrackerRoles().stream()
					.filter(a -> a.getExpenseTracker().getId().equals(id)).toList();
			if (assignments.isEmpty()) throw new IllegalStateException("Tracker member has no assigned role");
			return mapper.members(assignments).getFirst();
		});
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public ExpenseTrackerResponseDto members(@AuthenticationPrincipal User user, @PathVariable UUID id) {
		access.assertHasRoleOnExpenseTracker(id, user, EXPENSETRACKER_OWNER);
		return trackers.findById(id).map(mapper::toResponse)
				.orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Tracker not found"));
	}

	@PutMapping("/{id}/users/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setRoles(@AuthenticationPrincipal User user, @PathVariable UUID id, @PathVariable UUID userId,
						 @Valid @RequestBody TrackerRoleSelection selection) {
		access.assertHasRoleOnExpenseTracker(id, user, EXPENSETRACKER_OWNER);
		service.setRoles(user, id, userId, selection.roles(), selection.expectedRoles());
	}
}
