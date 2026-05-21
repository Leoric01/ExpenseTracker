package org.leoric.expensetracker.kanban.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagUpsertRequestDto;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanTagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/kanban-tag")
@RequiredArgsConstructor
public class KanbanTagController {

	private final KanbanTagService kanbanTagService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/{boardId}")
	public ResponseEntity<KanbanTagResponseDto> kanbanTagCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@Valid @RequestBody KanbanTagUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(kanbanTagService.kanbanTagCreate(currentUser, trackerId, boardId, request));
	}

	@PostMapping("/{trackerId}/{boardId}/bulk")
	public ResponseEntity<List<KanbanTagResponseDto>> kanbanTagCreateBulk(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@RequestBody List<@Valid KanbanTagUpsertRequestDto> request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(kanbanTagService.kanbanTagCreateBulk(currentUser, trackerId, boardId, request));
	}

	@GetMapping("/{trackerId}/{boardId}")
	public ResponseEntity<List<KanbanTagResponseDto>> kanbanTagFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanTagService.kanbanTagFindAll(currentUser, trackerId, boardId));
	}

	@PatchMapping("/{trackerId}/{boardId}/{tagId}")
	public ResponseEntity<KanbanTagResponseDto> kanbanTagUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID tagId,
			@Valid @RequestBody KanbanTagUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanTagService.kanbanTagUpdate(currentUser, trackerId, boardId, tagId, request));
	}

	@DeleteMapping("/{trackerId}/{boardId}/{tagId}")
	public ResponseEntity<Void> kanbanTagDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID tagId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanTagService.kanbanTagDelete(currentUser, trackerId, boardId, tagId);
		return ResponseEntity.noContent().build();
	}

	private void assertMemberOrOwner(UUID trackerId, User currentUser) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}
}