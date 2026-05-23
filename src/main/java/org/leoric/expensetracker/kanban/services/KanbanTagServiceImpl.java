package org.leoric.expensetracker.kanban.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanTagNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.handler.exceptions.KanbanTagNotFoundException;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagUpsertRequestDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanTag;
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
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final KanbanCardTagAssignmentRepository kanbanCardTagAssignmentRepository;
	private final KanbanMapper kanbanMapper;

	@Override
	@Transactional
	public KanbanTagResponseDto kanbanTagCreate(User currentUser, UUID trackerId, KanbanTagUpsertRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		String normalizedName = normalizeName(request.name());
		if (kanbanTagRepository.findByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, normalizedName).isPresent()) {
			throw new DuplicateKanbanTagNameException("Kanban tag with name '%s' already exists".formatted(normalizedName));
		}

		List<KanbanTag> existingTags = new ArrayList<>(
				kanbanTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(trackerId)
		);
		int targetOrder = OrderSequenceUtils.clampTargetOrder(request.tagOrder(), existingTags.size());

		KanbanTag tag = KanbanTag.builder()
				.expenseTracker(tracker)
				.name(normalizedName)
				.color(normalizeColor(request.color()))
				.tagOrder(targetOrder)
				.active(request.active() == null || request.active())
				.build();

		existingTags.add(targetOrder, tag);
		OrderSequenceUtils.normalizeContiguous(existingTags, KanbanTag::getTagOrder, KanbanTag::setTagOrder);
		kanbanTagRepository.saveAll(existingTags);
		log.info("User {} created kanban tag '{}' on tracker '{}'", currentUser.getEmail(), tag.getName(), trackerId);
		return kanbanMapper.toTagResponse(tag);
	}

	@Override
	@Transactional
	public List<KanbanTagResponseDto> kanbanTagCreateBulk(User currentUser, UUID trackerId, List<KanbanTagUpsertRequestDto> request) {
		if (request == null || request.isEmpty()) {
			throw new InvalidKanbanException("Kanban tag bulk payload must contain at least one item");
		}

		return request.stream()
				.map(item -> kanbanTagCreate(currentUser, trackerId, item))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<KanbanTagResponseDto> kanbanTagFindAll(User currentUser, UUID trackerId) {
		getTrackerOrThrow(trackerId);
		return kanbanTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(trackerId)
				.stream()
				.map(kanbanMapper::toTagResponse)
				.toList();
	}

	@Override
	@Transactional
	public KanbanTagResponseDto kanbanTagUpdate(User currentUser, UUID trackerId, UUID tagId, KanbanTagUpsertRequestDto request) {
		KanbanTag tag = getTagOrThrow(tagId);
		assertTagBelongsToTracker(tag, trackerId);
		String normalizedName = normalizeName(request.name());
		kanbanTagRepository.findByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, normalizedName)
				.filter(existing -> !existing.getId().equals(tagId))
				.ifPresent(ignored -> {
					throw new DuplicateKanbanTagNameException("Kanban tag with name '%s' already exists".formatted(normalizedName));
				});

		tag.setName(normalizedName);
		tag.setColor(normalizeColor(request.color()));
		if (request.active() != null) {
			tag.setActive(request.active());
		}

		List<KanbanTag> tags = new ArrayList<>(
				kanbanTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(trackerId)
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
	public void kanbanTagDelete(User currentUser, UUID trackerId, UUID tagId) {
		KanbanTag tag = getTagOrThrow(tagId);
		assertTagBelongsToTracker(tag, trackerId);
		tag.setDeleted(true);
		tag.setActive(false);
		kanbanTagRepository.save(tag);
		kanbanCardTagAssignmentRepository.deleteByTagId(tagId);

		List<KanbanTag> remainingTags = new ArrayList<>(
				kanbanTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(trackerId)
		);
		OrderSequenceUtils.normalizeContiguous(remainingTags, KanbanTag::getTagOrder, KanbanTag::setTagOrder);
		kanbanTagRepository.saveAll(remainingTags);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new InvalidKanbanException("Expense tracker not found with id: " + trackerId));
	}

	private KanbanTag getTagOrThrow(UUID tagId) {
		return kanbanTagRepository.findByIdAndIsDeletedFalse(tagId)
				.orElseThrow(() -> new KanbanTagNotFoundException("Kanban tag not found with id: " + tagId));
	}

	private void assertTagBelongsToTracker(KanbanTag tag, UUID trackerId) {
		if (!tag.getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanTagNotFoundException("Kanban tag not found on this expense tracker");
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