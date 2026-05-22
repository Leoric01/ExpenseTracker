package org.leoric.expensetracker.kanban.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanTagNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.handler.exceptions.KanbanBoardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanTagNotFoundException;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagUpsertRequestDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardTagAssignmentRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanTagRepository;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanTagService;
import org.leoric.expensetracker.utils.OrderSequenceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KanbanTagServiceImpl implements KanbanTagService {

	private final KanbanTagRepository kanbanTagRepository;
	private final KanbanBoardRepository kanbanBoardRepository;
	private final KanbanCardTagAssignmentRepository kanbanCardTagAssignmentRepository;
	private final KanbanMapper kanbanMapper;

	@Override
	@Transactional
	public KanbanTagResponseDto kanbanTagCreate(User currentUser, UUID trackerId, UUID boardId, KanbanTagUpsertRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		String normalizedName = normalizeName(request.name());
		if (kanbanTagRepository.findByBoardIdAndNameIgnoreCaseAndIsDeletedFalse(boardId, normalizedName).isPresent()) {
			throw new DuplicateKanbanTagNameException("Kanban tag with name '%s' already exists".formatted(normalizedName));
		}

		List<KanbanTag> existingTags = new ArrayList<>(
				kanbanTagRepository.findByBoardIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(boardId)
		);
		int targetOrder = OrderSequenceUtils.clampTargetOrder(request.tagOrder(), existingTags.size());

		KanbanTag tag = KanbanTag.builder()
				.board(board)
				.name(normalizedName)
				.color(normalizeColor(request.color()))
				.tagOrder(targetOrder)
				.active(request.active() == null || request.active())
				.build();

		existingTags.add(targetOrder, tag);
		OrderSequenceUtils.normalizeContiguous(existingTags, KanbanTag::getTagOrder, KanbanTag::setTagOrder);
		kanbanTagRepository.saveAll(existingTags);
		log.info("User {} created kanban tag '{}' on board '{}'", currentUser.getEmail(), tag.getName(), boardId);
		return kanbanMapper.toTagResponse(tag);
	}

	@Override
	@Transactional
	public List<KanbanTagResponseDto> kanbanTagCreateBulk(User currentUser, UUID trackerId, UUID boardId, List<KanbanTagUpsertRequestDto> request) {
		if (request == null || request.isEmpty()) {
			throw new InvalidKanbanException("Kanban tag bulk payload must contain at least one item");
		}

		return request.stream()
				.map(item -> kanbanTagCreate(currentUser, trackerId, boardId, item))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<KanbanTagResponseDto> kanbanTagFindAll(User currentUser, UUID trackerId, UUID boardId) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		return kanbanTagRepository.findByBoardIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(boardId)
				.stream()
				.map(kanbanMapper::toTagResponse)
				.toList();
	}

	@Override
	@Transactional
	public KanbanTagResponseDto kanbanTagUpdate(User currentUser, UUID trackerId, UUID boardId, UUID tagId, KanbanTagUpsertRequestDto request) {
		KanbanTag tag = getTagOrThrow(tagId);
		assertTagBelongsToBoard(tag, trackerId, boardId);
		String normalizedName = normalizeName(request.name());
		kanbanTagRepository.findByBoardIdAndNameIgnoreCaseAndIsDeletedFalse(boardId, normalizedName)
				.filter(existing -> !existing.getId().equals(tagId))
				.ifPresent(existing -> {
					throw new DuplicateKanbanTagNameException("Kanban tag with name '%s' already exists".formatted(normalizedName));
				});

		tag.setName(normalizedName);
		tag.setColor(normalizeColor(request.color()));
		if (request.active() != null) {
			tag.setActive(request.active());
		}

		List<KanbanTag> tags = new ArrayList<>(
				kanbanTagRepository.findByBoardIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(boardId)
		);
		tags.removeIf(t -> t.getId().equals(tagId));
		int targetOrder = OrderSequenceUtils.clampTargetOrder(
				request.tagOrder() == null ? tag.getTagOrder() : request.tagOrder(),
				tags.size()
		);
		tags.add(targetOrder, tag);
		OrderSequenceUtils.normalizeContiguous(tags, KanbanTag::getTagOrder, KanbanTag::setTagOrder);
		kanbanTagRepository.saveAll(tags);
		return kanbanMapper.toTagResponse(tag);
	}

	@Override
	@Transactional
	public void kanbanTagDelete(User currentUser, UUID trackerId, UUID boardId, UUID tagId) {
		KanbanTag tag = getTagOrThrow(tagId);
		assertTagBelongsToBoard(tag, trackerId, boardId);
		tag.setDeleted(true);
		tag.setActive(false);
		kanbanTagRepository.save(tag);
		kanbanCardTagAssignmentRepository.deleteByTagId(tagId);

		List<KanbanTag> remainingTags = new ArrayList<>(
				kanbanTagRepository.findByBoardIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(boardId)
		);
		OrderSequenceUtils.normalizeContiguous(remainingTags, KanbanTag::getTagOrder, KanbanTag::setTagOrder);
		kanbanTagRepository.saveAll(remainingTags);
	}

	private KanbanBoard getBoardOrThrow(UUID boardId) {
		return kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new KanbanBoardNotFoundException("Kanban board not found with id: " + boardId));
	}

	private KanbanTag getTagOrThrow(UUID tagId) {
		return kanbanTagRepository.findByIdAndIsDeletedFalse(tagId)
				.orElseThrow(() -> new KanbanTagNotFoundException("Kanban tag not found with id: " + tagId));
	}

	private void assertBoardBelongsToTracker(KanbanBoard board, UUID trackerId) {
		if (!board.getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanBoardNotFoundException("Kanban board does not belong to this expense tracker");
		}
	}

	private void assertTagBelongsToBoard(KanbanTag tag, UUID trackerId, UUID boardId) {
		if (!tag.getBoard().getId().equals(boardId) || !tag.getBoard().getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanTagNotFoundException("Kanban tag not found on this board");
		}
	}

	private String normalizeName(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidKanbanException("Tag name is required");
		}
		String normalized = value.trim();
		if (normalized.length() > 80) {
			throw new InvalidKanbanException("Tag name length must be <= 80");
		}
		return normalized;
	}

	private String normalizeColor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim().toUpperCase();
	}
}