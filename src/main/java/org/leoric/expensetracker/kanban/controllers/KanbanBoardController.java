package org.leoric.expensetracker.kanban.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardDetailResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageUpsertRequestDto;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanBoardService;
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
@RequestMapping("/api/kanban-board")
@RequiredArgsConstructor
public class KanbanBoardController {

	private final KanbanBoardService kanbanBoardService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<KanbanBoardResponseDto> kanbanBoardCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody KanbanBoardUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(kanbanBoardService.kanbanBoardCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<List<KanbanBoardResponseDto>> kanbanBoardFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanBoardService.kanbanBoardFindAll(currentUser, trackerId));
	}

	@GetMapping("/{trackerId}/{boardId}")
	public ResponseEntity<KanbanBoardDetailResponseDto> kanbanBoardFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanBoardService.kanbanBoardFindById(currentUser, trackerId, boardId));
	}

	@PatchMapping("/{trackerId}/{boardId}")
	public ResponseEntity<KanbanBoardResponseDto> kanbanBoardUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@Valid @RequestBody KanbanBoardUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanBoardService.kanbanBoardUpdate(currentUser, trackerId, boardId, request));
	}

	@PatchMapping("/{trackerId}/reorder")
	public ResponseEntity<Void> kanbanBoardReorder(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody KanbanBoardReorderRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanBoardService.kanbanBoardReorder(currentUser, trackerId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{trackerId}/{boardId}")
	public ResponseEntity<Void> kanbanBoardDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanBoardService.kanbanBoardDelete(currentUser, trackerId, boardId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{trackerId}/{boardId}/stage")
	public ResponseEntity<KanbanStageResponseDto> kanbanStageCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@Valid @RequestBody KanbanStageUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(kanbanBoardService.kanbanStageCreate(currentUser, trackerId, boardId, request));
	}

	@GetMapping("/{trackerId}/{boardId}/stage")
	public ResponseEntity<List<KanbanStageResponseDto>> kanbanStageFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanBoardService.kanbanStageFindAll(currentUser, trackerId, boardId));
	}

	@PatchMapping("/{trackerId}/{boardId}/stage/{stageId}")
	public ResponseEntity<KanbanStageResponseDto> kanbanStageUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID stageId,
			@Valid @RequestBody KanbanStageUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanBoardService.kanbanStageUpdate(currentUser, trackerId, boardId, stageId, request));
	}

	@PatchMapping("/{trackerId}/{boardId}/stage/reorder")
	public ResponseEntity<Void> kanbanStageReorder(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@Valid @RequestBody KanbanStageReorderRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanBoardService.kanbanStageReorder(currentUser, trackerId, boardId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{trackerId}/{boardId}/stage/{stageId}")
	public ResponseEntity<Void> kanbanStageDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID stageId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanBoardService.kanbanStageDelete(currentUser, trackerId, boardId, stageId);
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