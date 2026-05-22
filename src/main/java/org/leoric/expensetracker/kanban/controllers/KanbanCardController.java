package org.leoric.expensetracker.kanban.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardSnapshotResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardMoveStageRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardUpsertRequestDto;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanCardService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/kanban-card")
@RequiredArgsConstructor
public class KanbanCardController {

	private final KanbanCardService kanbanCardService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/{boardId}")
	public ResponseEntity<KanbanCardResponseDto> kanbanCardCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@Valid @RequestBody KanbanCardUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(kanbanCardService.kanbanCardCreate(currentUser, trackerId, boardId, request));
	}

	@GetMapping("/{trackerId}/{boardId}")
	public ResponseEntity<KanbanBoardSnapshotResponseDto> kanbanCardFindBoardSnapshot(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@RequestParam(required = false) Boolean visibleOnly
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanCardService.kanbanCardFindBoardSnapshot(currentUser, trackerId, boardId, visibleOnly));
	}

	@GetMapping("/{trackerId}/{boardId}/{cardId}")
	public ResponseEntity<KanbanCardResponseDto> kanbanCardFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID cardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanCardService.kanbanCardFindById(currentUser, trackerId, boardId, cardId));
	}

	@PatchMapping("/{trackerId}/{boardId}/{cardId}")
	public ResponseEntity<KanbanCardResponseDto> kanbanCardUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID cardId,
			@Valid @RequestBody KanbanCardUpsertRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanCardService.kanbanCardUpdate(currentUser, trackerId, boardId, cardId, request));
	}

	@PatchMapping("/{trackerId}/{cardId}/move")
	public ResponseEntity<KanbanCardResponseDto> kanbanCardMove(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID cardId,
			@Valid @RequestBody KanbanCardMoveStageRequestDto request
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanCardService.kanbanCardMove(currentUser, trackerId, cardId, request.targetStageId()));
	}

	@DeleteMapping("/{trackerId}/{boardId}/{cardId}")
	public ResponseEntity<Void> kanbanCardDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID cardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanCardService.kanbanCardDelete(currentUser, trackerId, boardId, cardId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{trackerId}/{boardId}/{cardId}/image")
	public ResponseEntity<KanbanCardResponseDto> kanbanCardUploadImage(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID cardId,
			@RequestParam("file") MultipartFile file
	) {
		assertMemberOrOwner(trackerId, currentUser);
		return ResponseEntity.ok(kanbanCardService.kanbanCardUploadImage(currentUser, trackerId, boardId, cardId, file));
	}

	@DeleteMapping("/{trackerId}/{boardId}/{cardId}/image")
	public ResponseEntity<Void> kanbanCardDeleteImage(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID boardId,
			@PathVariable UUID cardId
	) {
		assertMemberOrOwner(trackerId, currentUser);
		kanbanCardService.kanbanCardDeleteImage(currentUser, trackerId, boardId, cardId);
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