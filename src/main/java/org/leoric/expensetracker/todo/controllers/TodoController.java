package org.leoric.expensetracker.todo.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.todo.dtos.TodoArchiveUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoStatusUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoTagReplaceRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoUpsertRequestDto;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
import org.leoric.expensetracker.todo.services.interfaces.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/todo")
@RequiredArgsConstructor
public class TodoController {

	private final TodoService todoService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<TodoResponseDto> todoCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody TodoUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(todoService.todoCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/tree")
	public ResponseEntity<List<TodoResponseDto>> todoFindTree(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) TodoStatus status,
			@RequestParam(required = false) TodoPriority priority,
			@RequestParam(required = false) LocalDate dueFrom,
			@RequestParam(required = false) LocalDate dueTo
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(todoService.todoFindTree(
				currentUser,
				trackerId,
				search,
				active,
				status,
				priority,
				dueFrom,
				dueTo
		));
	}

	@GetMapping("/{trackerId}/{todoId}")
	public ResponseEntity<TodoResponseDto> todoFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(todoService.todoFindById(currentUser, trackerId, todoId));
	}

	@PatchMapping("/{trackerId}/{todoId}")
	public ResponseEntity<TodoResponseDto> todoUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId,
			@Valid @RequestBody TodoUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(todoService.todoUpdate(currentUser, trackerId, todoId, request));
	}

	@PatchMapping("/{trackerId}/{todoId}/status")
	public ResponseEntity<Void> todoUpdateStatus(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId,
			@Valid @RequestBody TodoStatusUpdateRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		todoService.todoUpdateStatus(currentUser, trackerId, todoId, request);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{trackerId}/{todoId}/archive")
	public ResponseEntity<Void> todoArchive(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId,
			@Valid @RequestBody TodoArchiveUpdateRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		todoService.todoArchive(currentUser, trackerId, todoId, request);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{trackerId}/{todoId}/tags")
	public ResponseEntity<Void> todoReplaceTags(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId,
			@RequestBody TodoTagReplaceRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		todoService.todoReplaceTags(currentUser, trackerId, todoId, request != null ? request.tagIds() : null);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{trackerId}/{todoId}")
	public ResponseEntity<Void> todoDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID todoId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		todoService.todoDelete(currentUser, trackerId, todoId);
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