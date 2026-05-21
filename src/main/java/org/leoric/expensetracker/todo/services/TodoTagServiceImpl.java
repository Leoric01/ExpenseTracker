package org.leoric.expensetracker.todo.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateTodoTagNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidTodoException;
import org.leoric.expensetracker.handler.exceptions.TodoTagNotFoundException;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoTagUpsertRequestDto;
import org.leoric.expensetracker.todo.mapstruct.TodoMapper;
import org.leoric.expensetracker.todo.models.TodoTag;
import org.leoric.expensetracker.todo.repositories.TodoTagAssignmentRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagRepository;
import org.leoric.expensetracker.todo.services.interfaces.TodoTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TodoTagServiceImpl implements TodoTagService {

	private final TodoTagRepository todoTagRepository;
	private final TodoTagAssignmentRepository todoTagAssignmentRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final TodoMapper todoMapper;

	@Override
	@Transactional
	public TodoTagResponseDto todoTagCreate(User currentUser, UUID trackerId, TodoTagUpsertRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		String normalizedName = normalizeName(request.name());

		if (todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, normalizedName)) {
			throw new DuplicateTodoTagNameException("Todo tag with name '%s' already exists".formatted(normalizedName));
		}

		TodoTag tag = TodoTag.builder()
				.expenseTracker(tracker)
				.name(normalizedName)
				.color(normalizeColor(request.color()))
				.active(request.active() == null || request.active())
				.build();

		tag = todoTagRepository.save(tag);
		log.info("User {} created todo tag '{}' in tracker '{}'", currentUser.getEmail(), tag.getName(), trackerId);
		return todoMapper.toTagResponseDto(tag);
	}

	@Override
	@Transactional
	public List<TodoTagResponseDto> todoTagCreateBulk(User currentUser, UUID trackerId, List<TodoTagUpsertRequestDto> request) {
		if (request == null || request.isEmpty()) {
			throw new InvalidTodoException("Todo tag bulk payload must contain at least one item");
		}

		return request.stream()
				.map(item -> todoTagCreate(currentUser, trackerId, item))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TodoTagResponseDto> todoTagFindAll(User currentUser, UUID trackerId) {
		return todoTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByNameAsc(trackerId)
				.stream()
				.map(todoMapper::toTagResponseDto)
				.toList();
	}

	@Override
	@Transactional
	public TodoTagResponseDto todoTagUpdate(User currentUser, UUID trackerId, UUID tagId, TodoTagUpsertRequestDto request) {
		TodoTag tag = getTagOrThrow(tagId);
		assertTagBelongsToTracker(tag, trackerId);
		String normalizedName = normalizeName(request.name());

		if (todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, normalizedName, tagId)) {
			throw new DuplicateTodoTagNameException("Todo tag with name '%s' already exists".formatted(normalizedName));
		}

		tag.setName(normalizedName);
		tag.setColor(normalizeColor(request.color()));
		if (request.active() != null) {
			tag.setActive(request.active());
		}

		tag = todoTagRepository.save(tag);
		log.info("User {} updated todo tag '{}' in tracker '{}'", currentUser.getEmail(), tag.getName(), trackerId);
		return todoMapper.toTagResponseDto(tag);
	}

	@Override
	@Transactional
	public void todoTagDelete(User currentUser, UUID trackerId, UUID tagId) {
		TodoTag tag = getTagOrThrow(tagId);
		assertTagBelongsToTracker(tag, trackerId);

		tag.setDeleted(true);
		tag.setActive(false);
		todoTagRepository.save(tag);
		todoTagAssignmentRepository.deleteByTagId(tagId);

		log.info("User {} deleted todo tag '{}' in tracker '{}'", currentUser.getEmail(), tag.getName(), trackerId);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("ExpenseTracker not found with id: " + trackerId));
	}

	private TodoTag getTagOrThrow(UUID tagId) {
		return todoTagRepository.findByIdAndIsDeletedFalse(tagId)
				.orElseThrow(() -> new TodoTagNotFoundException("TodoTag not found with id: " + tagId));
	}

	private void assertTagBelongsToTracker(TodoTag tag, UUID trackerId) {
		if (!tag.getExpenseTracker().getId().equals(trackerId)) {
			throw new TodoTagNotFoundException("Todo tag does not belong to this expense tracker");
		}
	}

	private String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidTodoException("Todo tag name is required");
		}
		return name.trim();
	}

	private String normalizeColor(String color) {
		if (color == null || color.isBlank()) {
			return null;
		}
		return color.trim().toUpperCase();
	}
}