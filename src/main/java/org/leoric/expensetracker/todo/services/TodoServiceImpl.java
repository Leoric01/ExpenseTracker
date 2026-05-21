package org.leoric.expensetracker.todo.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateTodoTitleException;
import org.leoric.expensetracker.handler.exceptions.InvalidTodoException;
import org.leoric.expensetracker.handler.exceptions.TodoNotFoundException;
import org.leoric.expensetracker.handler.exceptions.TodoTagNotFoundException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.todo.dtos.TodoArchiveUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoStatusUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoUpsertRequestDto;
import org.leoric.expensetracker.todo.mapstruct.TodoMapper;
import org.leoric.expensetracker.todo.models.Todo;
import org.leoric.expensetracker.todo.models.TodoTag;
import org.leoric.expensetracker.todo.models.TodoTagAssignment;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
import org.leoric.expensetracker.todo.repositories.TodoRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagAssignmentRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagRepository;
import org.leoric.expensetracker.todo.services.interfaces.TodoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

	private static final int MAX_TREE_DEPTH = 15;
	private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/heic"
	);
	private static final Comparator<Todo> TODO_ORDER = Comparator
			.comparing((Todo t) -> t.getSortOrder() == null ? 0 : t.getSortOrder())
			.thenComparing(Todo::getTitle, String.CASE_INSENSITIVE_ORDER);

	private final TodoRepository todoRepository;
	private final TodoTagRepository todoTagRepository;
	private final TodoTagAssignmentRepository todoTagAssignmentRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ImageService imageService;
	private final TodoMapper todoMapper;

	@Override
	@Transactional
	public TodoResponseDto todoCreate(User currentUser, UUID trackerId, TodoUpsertRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		String normalizedTitle = normalizeTitle(request.title());
		Todo parent = resolveParent(trackerId, request.parentTodoId(), null);
		assertUniqueTitle(trackerId, parent, normalizedTitle, null);
		List<TodoTag> tags = resolveTags(trackerId, request.tagIds());

		Todo todo = Todo.builder()
				.expenseTracker(tracker)
				.parent(parent)
				.title(normalizedTitle)
				.description(normalizeNullable(request.description()))
				.imageUrl(normalizeImageUrl(request.imageUrl()))
				.status(request.status() != null ? request.status() : TodoStatus.TODO)
				.priority(request.priority() != null ? request.priority() : TodoPriority.MEDIUM)
				.dueDate(request.dueDate())
				.completedAt(null)
				.archivedAt(null)
				.estimatedMinutes(request.estimatedMinutes())
				.note(normalizeNullable(request.note()))
				.sortOrder(defaultIfNull(request.sortOrder()))
				.active(request.active() == null || request.active())
				.build();

		if (todo.getStatus() == TodoStatus.DONE) {
			todo.setCompletedAt(Instant.now());
		}

		todo = todoRepository.save(todo);
		replaceTodoTags(todo, tags);

		log.info("User {} created todo '{}' in tracker '{}'", currentUser.getEmail(), todo.getTitle(), tracker.getName());
		return buildTreeForRoot(todo, Map.of(todo.getId(), tags));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TodoResponseDto> todoFindTree(
			User currentUser,
			UUID trackerId,
			String search,
			Boolean active,
			TodoStatus status,
			TodoPriority priority,
			LocalDate dueFrom,
			LocalDate dueTo
	) {
		if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
			throw new InvalidTodoException("dueFrom must be before or equal to dueTo");
		}

		List<Todo> todos = todoRepository.findAllByExpenseTrackerIdWithFilters(
				trackerId,
				normalizeSearch(search),
				active,
				status,
				priority,
				dueFrom,
				dueTo
		);

		Map<UUID, List<TodoTag>> tagsByTodoId = loadTagsByTodoIds(todos.stream().map(Todo::getId).toList());
		return buildTree(todos, tagsByTodoId);
	}

	@Override
	@Transactional(readOnly = true)
	public TodoResponseDto todoFindById(User currentUser, UUID trackerId, UUID todoId) {
		Todo root = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(root, trackerId);

		List<Todo> subtree = collectSubtree(root);
		Map<UUID, List<TodoTag>> tagsByTodoId = loadTagsByTodoIds(subtree.stream().map(Todo::getId).toList());
		return buildTreeForRoot(root, tagsByTodoId);
	}

	@Override
	@Transactional
	public TodoResponseDto todoUpdate(User currentUser, UUID trackerId, UUID todoId, TodoUpsertRequestDto request) {
		Todo todo = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(todo, trackerId);

		String normalizedTitle = normalizeTitle(request.title());
		Todo parent = resolveParent(trackerId, request.parentTodoId(), todoId);
		int targetParentLevel = parent == null ? 0 : computeLevel(parent);
		int subtreeHeight = computeSubtreeHeight(todo);
		if (targetParentLevel + subtreeHeight > MAX_TREE_DEPTH) {
			throw new InvalidTodoException("Todo tree depth limit exceeded. Maximum depth is %d".formatted(MAX_TREE_DEPTH));
		}
		assertUniqueTitle(trackerId, parent, normalizedTitle, todoId);

		todo.setParent(parent);
		todo.setTitle(normalizedTitle);
		todo.setDescription(normalizeNullable(request.description()));
		todo.setImageUrl(normalizeImageUrl(request.imageUrl()));
		todo.setStatus(request.status() != null ? request.status() : TodoStatus.TODO);
		todo.setPriority(request.priority() != null ? request.priority() : TodoPriority.MEDIUM);
		todo.setDueDate(request.dueDate());
		todo.setEstimatedMinutes(request.estimatedMinutes());
		todo.setNote(normalizeNullable(request.note()));
		todo.setSortOrder(defaultIfNull(request.sortOrder()));
		if (request.active() != null) {
			todo.setActive(request.active());
		}

		if (todo.getStatus() == TodoStatus.DONE) {
			todo.setCompletedAt(todo.getCompletedAt() != null ? todo.getCompletedAt() : Instant.now());
		} else {
			todo.setCompletedAt(null);
		}

		todo = todoRepository.save(todo);
		if (request.tagIds() != null) {
			List<TodoTag> tags = resolveTags(trackerId, request.tagIds());
			replaceTodoTags(todo, tags);
		}

		log.info("User {} updated todo '{}' in tracker '{}'", currentUser.getEmail(), todo.getTitle(), trackerId);
		return todoFindById(currentUser, trackerId, todoId);
	}

	@Override
	@Transactional
	public void todoUpdateStatus(User currentUser, UUID trackerId, UUID todoId, TodoStatusUpdateRequestDto request) {
		Todo root = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(root, trackerId);

		Instant completedAt = request.status() == TodoStatus.DONE ? Instant.now() : null;
		List<Todo> subtree = collectSubtree(root);
		for (Todo todo : subtree) {
			todo.setStatus(request.status());
			todo.setCompletedAt(completedAt);
		}
		todoRepository.saveAll(subtree);

		log.info("User {} changed todo status to {} on subtree rooted by {} in tracker '{}'",
		         currentUser.getEmail(), request.status(), todoId, trackerId);
	}

	@Override
	@Transactional
	public void todoArchive(User currentUser, UUID trackerId, UUID todoId, TodoArchiveUpdateRequestDto request) {
		Todo root = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(root, trackerId);

		Instant archivedAt = request.archived() ? Instant.now() : null;
		List<Todo> subtree = collectSubtree(root);
		for (Todo todo : subtree) {
			todo.setArchivedAt(archivedAt);
			todo.setActive(!request.archived());
		}
		todoRepository.saveAll(subtree);

		log.info("User {} {} todo subtree rooted by {} in tracker '{}'",
		         currentUser.getEmail(), request.archived() ? "archived" : "unarchived", todoId, trackerId);
	}

	@Override
	@Transactional
	public void todoReplaceTags(User currentUser, UUID trackerId, UUID todoId, List<UUID> tagIds) {
		Todo todo = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(todo, trackerId);

		List<TodoTag> tags = resolveTags(trackerId, tagIds);
		replaceTodoTags(todo, tags);
		log.info("User {} replaced tags on todo '{}' in tracker '{}'", currentUser.getEmail(), todo.getTitle(), trackerId);
	}

	@Override
	@Transactional
	public TodoResponseDto todoUploadImage(User currentUser, UUID trackerId, UUID todoId, MultipartFile file) {
		Todo todo = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(todo, trackerId);

		if (file == null || file.isEmpty()) {
			throw new InvalidTodoException("Image file is required");
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
			throw new InvalidTodoException(
					"Unsupported image type '%s'. Allowed: JPEG, PNG, WEBP, HEIC".formatted(contentType)
			);
		}

		String imageUrl = imageService.uploadImage(file, "expense-tracker/todo");
		todo.setImageUrl(imageUrl);
		todoRepository.save(todo);

		log.info("User {} uploaded image for todo '{}' in tracker '{}'", currentUser.getEmail(), todo.getTitle(), trackerId);
		return todoFindById(currentUser, trackerId, todoId);
	}

	@Override
	@Transactional
	public void todoDeleteImage(User currentUser, UUID trackerId, UUID todoId) {
		Todo todo = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(todo, trackerId);

		if (todo.getImageUrl() == null || todo.getImageUrl().isBlank()) {
			return;
		}

		boolean deleted = imageService.deleteImageByUrl(todo.getImageUrl());
		if (!deleted) {
			throw new InvalidTodoException("Failed to delete image from Cloudinary. Todo image reference was not changed");
		}

		todo.setImageUrl(null);
		todoRepository.save(todo);

		log.info("User {} removed image from todo '{}' in tracker '{}'", currentUser.getEmail(), todo.getTitle(), trackerId);
	}

	@Override
	@Transactional
	public void todoDelete(User currentUser, UUID trackerId, UUID todoId) {
		Todo root = getTodoOrThrow(todoId);
		assertTodoBelongsToTracker(root, trackerId);

		List<Todo> subtree = collectSubtree(root);
		Instant archivedAt = Instant.now();
		for (Todo todo : subtree) {
			todo.setDeleted(true);
			todo.setActive(false);
			todo.setArchivedAt(todo.getArchivedAt() != null ? todo.getArchivedAt() : archivedAt);
		}
		todoRepository.saveAll(subtree);
		todoTagAssignmentRepository.deleteByTodoIdIn(subtree.stream().map(Todo::getId).toList());

		log.info("User {} soft deleted todo subtree rooted by {} in tracker '{}'",
		         currentUser.getEmail(), todoId, trackerId);
	}

	private void replaceTodoTags(Todo todo, List<TodoTag> tags) {
		todoTagAssignmentRepository.deleteByTodoId(todo.getId());
		if (tags.isEmpty()) {
			return;
		}
		List<TodoTagAssignment> assignments = tags.stream()
				.distinct()
				.map(tag -> TodoTagAssignment.builder().todo(todo).tag(tag).build())
				.toList();
		todoTagAssignmentRepository.saveAll(assignments);
	}

	private List<TodoTag> resolveTags(UUID trackerId, List<UUID> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) {
			return List.of();
		}
		Set<UUID> uniqueIds = new HashSet<>(tagIds);
		List<TodoTag> tags = todoTagRepository.findByExpenseTrackerIdAndIdInAndIsDeletedFalse(trackerId, uniqueIds);
		if (tags.size() != uniqueIds.size()) {
			throw new TodoTagNotFoundException("Some tags were not found in this expense tracker");
		}
		return tags;
	}

	private Todo resolveParent(UUID trackerId, UUID parentTodoId, UUID currentTodoId) {
		if (parentTodoId == null) {
			return null;
		}

		Todo parent = getTodoOrThrow(parentTodoId);
		assertTodoBelongsToTracker(parent, trackerId);

		if (currentTodoId != null) {
			if (parent.getId().equals(currentTodoId)) {
				throw new InvalidTodoException("Todo cannot be a parent of itself");
			}

			Todo cursor = parent;
			while (cursor != null) {
				if (cursor.getId().equals(currentTodoId)) {
					throw new InvalidTodoException("Invalid todo tree move. Circular relationship detected");
				}
				cursor = cursor.getParent();
			}
		}

		int level = computeLevel(parent) + 1;
		if (level > MAX_TREE_DEPTH) {
			throw new InvalidTodoException("Todo tree depth limit exceeded. Maximum depth is %d".formatted(MAX_TREE_DEPTH));
		}

		return parent;
	}

	private int computeLevel(Todo todo) {
		int level = 1;
		Todo cursor = todo;
		while (cursor.getParent() != null) {
			level++;
			cursor = cursor.getParent();
		}
		return level;
	}

	private int computeSubtreeHeight(Todo root) {
		int height = 1;
		int level = 1;
		List<UUID> currentLevelIds = List.of(root.getId());

		while (!currentLevelIds.isEmpty()) {
			List<Todo> children = todoRepository.findByExpenseTrackerIdAndParentIdInAndIsDeletedFalse(
					root.getExpenseTracker().getId(),
					currentLevelIds
			);
			if (children.isEmpty()) {
				break;
			}
			level++;
			height = Math.max(height, level);
			currentLevelIds = children.stream().map(Todo::getId).toList();
		}

		return height;
	}

	private void assertUniqueTitle(UUID trackerId, Todo parent, String title, UUID currentTodoId) {
		boolean exists;
		if (parent == null) {
			exists = currentTodoId == null
					? todoRepository.existsByExpenseTrackerIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalse(trackerId, title)
					: todoRepository.existsByExpenseTrackerIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, title, currentTodoId);
		} else {
			exists = currentTodoId == null
					? todoRepository.existsByExpenseTrackerIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalse(trackerId, parent.getId(), title)
					: todoRepository.existsByExpenseTrackerIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, parent.getId(), title, currentTodoId);
		}

		if (exists) {
			throw new DuplicateTodoTitleException("Todo with title '%s' already exists under this parent".formatted(title));
		}
	}

	private List<Todo> collectSubtree(Todo root) {
		List<Todo> result = new ArrayList<>();
		ArrayDeque<Todo> queue = new ArrayDeque<>();
		queue.add(root);

		while (!queue.isEmpty()) {
			Todo current = queue.poll();
			result.add(current);

			List<Todo> children = todoRepository.findByExpenseTrackerIdAndParentIdInAndIsDeletedFalse(
					current.getExpenseTracker().getId(),
					List.of(current.getId())
			);
			children.stream().sorted(TODO_ORDER).forEach(queue::add);
		}

		return result;
	}

	private Map<UUID, List<TodoTag>> loadTagsByTodoIds(Collection<UUID> todoIds) {
		if (todoIds.isEmpty()) {
			return Map.of();
		}

		return todoTagAssignmentRepository.findAllByTodoIdsWithTag(todoIds)
				.stream()
				.collect(Collectors.groupingBy(
						a -> a.getTodo().getId(),
						Collectors.mapping(TodoTagAssignment::getTag, Collectors.toList())
				));
	}

	private List<TodoResponseDto> buildTree(List<Todo> todos, Map<UUID, List<TodoTag>> tagsByTodoId) {
		if (todos.isEmpty()) {
			return List.of();
		}

		Map<UUID, Todo> byId = todos.stream().collect(Collectors.toMap(Todo::getId, Function.identity()));
		Map<UUID, List<Todo>> childrenByParentId = new HashMap<>();
		List<Todo> roots = new ArrayList<>();

		for (Todo todo : todos) {
			Todo parent = todo.getParent();
			if (parent == null || !byId.containsKey(parent.getId())) {
				roots.add(todo);
				continue;
			}
			childrenByParentId.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(todo);
		}

		roots.sort(TODO_ORDER);
		childrenByParentId.values().forEach(list -> list.sort(TODO_ORDER));

		return roots.stream()
				.map(root -> toDtoRecursive(root, childrenByParentId, tagsByTodoId))
				.toList();
	}

	private TodoResponseDto buildTreeForRoot(Todo root, Map<UUID, List<TodoTag>> tagsByTodoId) {
		List<Todo> subtree = collectSubtree(root);
		Map<UUID, List<TodoTag>> effectiveTags = tagsByTodoId.isEmpty()
				? loadTagsByTodoIds(subtree.stream().map(Todo::getId).toList())
				: tagsByTodoId;
		Map<UUID, List<Todo>> childrenByParentId = subtree.stream()
				.filter(t -> t.getParent() != null)
				.collect(Collectors.groupingBy(t -> t.getParent().getId()));
		childrenByParentId.values().forEach(list -> list.sort(TODO_ORDER));
		return toDtoRecursive(root, childrenByParentId, effectiveTags);
	}

	private TodoResponseDto toDtoRecursive(
			Todo todo,
			Map<UUID, List<Todo>> childrenByParentId,
			Map<UUID, List<TodoTag>> tagsByTodoId
	) {
		List<TodoResponseDto> children = childrenByParentId.getOrDefault(todo.getId(), List.of()).stream()
				.map(child -> toDtoRecursive(child, childrenByParentId, tagsByTodoId))
				.toList();

		List<TodoTagResponseDto> tags = tagsByTodoId
				.getOrDefault(todo.getId(), List.of())
				.stream()
				.map(todoMapper::toTagResponseDto)
				.toList();

		return todoMapper.toResponseDto(todo, tags, children);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("ExpenseTracker not found with id: " + trackerId));
	}

	private Todo getTodoOrThrow(UUID todoId) {
		return todoRepository.findByIdAndIsDeletedFalse(todoId)
				.orElseThrow(() -> new TodoNotFoundException("Todo not found with id: " + todoId));
	}

	private void assertTodoBelongsToTracker(Todo todo, UUID trackerId) {
		if (!todo.getExpenseTracker().getId().equals(trackerId)) {
			throw new TodoNotFoundException("Todo does not belong to this expense tracker");
		}
	}

	private String normalizeTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new InvalidTodoException("Todo title is required");
		}
		return title.trim();
	}

	private String normalizeNullable(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeSearch(String search) {
		return normalizeNullable(search);
	}

	private String normalizeImageUrl(String imageUrl) {
		String normalized = normalizeNullable(imageUrl);
		if (normalized == null) {
			return null;
		}

		if (!normalized.startsWith("https://res.cloudinary.com/")
				&& !normalized.startsWith("http://res.cloudinary.com/")) {
			throw new InvalidTodoException("imageUrl must be a valid Cloudinary URL");
		}

		return normalized;
	}

	private Integer defaultIfNull(Integer value) {
		return value == null ? 0 : value;
	}
}