package org.leoric.expensetracker.todo.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoTagUpsertRequestDto;
import org.leoric.expensetracker.todo.services.interfaces.TodoTagService;
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
@RequestMapping("/api/todo-tag")
@RequiredArgsConstructor
public class TodoTagController {

	private final TodoTagService todoTagService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<TodoTagResponseDto> todoTagCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody TodoTagUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(todoTagService.todoTagCreate(currentUser, trackerId, request));
	}

	@PostMapping("/{trackerId}/bulk")
	public ResponseEntity<List<TodoTagResponseDto>> todoTagCreateBulk(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestBody List<@Valid TodoTagUpsertRequestDto> request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(todoTagService.todoTagCreateBulk(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<List<TodoTagResponseDto>> todoTagFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(todoTagService.todoTagFindAll(currentUser, trackerId));
	}

	@PatchMapping("/{trackerId}/{tagId}")
	public ResponseEntity<TodoTagResponseDto> todoTagUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID tagId,
			@Valid @RequestBody TodoTagUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(todoTagService.todoTagUpdate(currentUser, trackerId, tagId, request));
	}

	@DeleteMapping("/{trackerId}/{tagId}")
	public ResponseEntity<Void> todoTagDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID tagId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		todoTagService.todoTagDelete(currentUser, trackerId, tagId);
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