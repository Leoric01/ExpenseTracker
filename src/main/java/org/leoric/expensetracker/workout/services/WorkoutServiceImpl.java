package org.leoric.expensetracker.workout.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.InvalidWorkoutException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanResponseDto;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanUpsertRequestDto;
import org.leoric.expensetracker.workout.models.WorkoutDay;
import org.leoric.expensetracker.workout.models.WorkoutExercise;
import org.leoric.expensetracker.workout.models.WorkoutPlan;
import org.leoric.expensetracker.workout.repositories.WorkoutPlanRepository;
import org.leoric.expensetracker.workout.services.interfaces.WorkoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkoutServiceImpl implements WorkoutService {
	private final WorkoutPlanRepository workoutPlanRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ImageService imageService;
	private static final Set<String> IMAGE_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/heic", "image/gif", "image/avif");

	@Override
	@Transactional(readOnly = true)
	public WorkoutPlanResponseDto workoutFind(User currentUser, UUID trackerId) {
		return toResponse(workoutPlanRepository.findByExpenseTrackerId(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Workout plan not found")));
	}

	@Override
	@Transactional
	public WorkoutPlanResponseDto workoutUpsert(User currentUser, UUID trackerId, WorkoutPlanUpsertRequestDto request) {
		validateUniqueIds(request);
		WorkoutPlan plan = workoutPlanRepository.findLockedByTrackerId(trackerId).orElseGet(() -> {
			WorkoutPlan newPlan = new WorkoutPlan();
			newPlan.setExpenseTracker(expenseTrackerRepository.findById(trackerId)
					.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found")));
			return newPlan;
		});
		assertNoImagesOnRemovedExercises(plan, request);
		plan.setTitle(request.title());
		plan.setWeekLabel(request.weekLabel());

		Map<String, WorkoutDay> existingDays = plan.getDays().stream()
				.collect(Collectors.toMap(WorkoutDay::getKey, Function.identity()));
		Set<String> retainedDays = new HashSet<>();
		for (int i = 0; i < request.days().size(); i++) {
			var dayRequest = request.days().get(i);
			retainedDays.add(dayRequest.id());
			WorkoutDay day = existingDays.get(dayRequest.id());
			if (day == null) {
				day = new WorkoutDay();
				day.setPlan(plan);
				day.setKey(dayRequest.id());
				plan.getDays().add(day);
			}
			day.setLabel(dayRequest.label());
			day.setName(dayRequest.name());
			day.setFocus(dayRequest.focus());
			day.setSortOrder(i);
			updateExercises(day, dayRequest);
		}
		plan.getDays().removeIf(day -> !retainedDays.contains(day.getKey()));
		workoutPlanRepository.save(plan);
		log.info("User {} updated workout plan in tracker {}", currentUser.getEmail(), trackerId);
		return toResponse(plan);
	}

	@Override
	@Transactional
	public void workoutSetCompletion(User currentUser, UUID trackerId, String dayId, String exerciseId, boolean completed) {
		WorkoutPlan plan = getLockedPlan(trackerId);
		WorkoutExercise exercise = findExercise(plan, dayId, exerciseId);
		exercise.setCompleted(completed);
		log.info("User {} set workout completion in tracker {}", currentUser.getEmail(), trackerId);
	}

	@Override
	@Transactional
	public WorkoutPlanResponseDto workoutUploadImage(User currentUser, UUID trackerId, String dayId,
			String exerciseId, MultipartFile file) {
		WorkoutPlan plan = getLockedPlan(trackerId);
		WorkoutExercise exercise = findExercise(plan, dayId, exerciseId);
		if (exercise.getImageUrl() != null) {
			throw new InvalidWorkoutException("Remove the existing image before uploading another one");
		}
		if (file == null || file.isEmpty() || file.getContentType() == null || !IMAGE_TYPES.contains(file.getContentType())) {
			throw new InvalidWorkoutException("A JPEG, PNG, WEBP, HEIC, GIF or AVIF image is required");
		}
		String url = imageService.uploadImage(file, "expense-tracker/workout");
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED && !imageService.deleteImageByUrl(url)) {
					log.error("Failed to clean up uploaded workout image after transaction rollback: {}", url);
				}
			}
		});
		exercise.setImageUrl(url);
		log.info("User {} uploaded workout image in tracker {}", currentUser.getEmail(), trackerId);
		return toResponse(plan);
	}

	@Override
	@Transactional
	public void workoutDeleteImage(User currentUser, UUID trackerId, String dayId, String exerciseId) {
		WorkoutPlan plan = getLockedPlan(trackerId);
		WorkoutExercise exercise = findExercise(plan, dayId, exerciseId);
		if (exercise.getImageUrl() == null) return;
		String url = exercise.getImageUrl();
		exercise.setImageUrl(null);
		workoutPlanRepository.saveAndFlush(plan);
		if (!imageService.deleteImageByUrl(url)) {
			throw new InvalidWorkoutException("Image could not be deleted; its reference was not changed");
		}
		log.info("User {} deleted workout image in tracker {}", currentUser.getEmail(), trackerId);
	}

	@Override
	@Transactional
	public void workoutReset(User currentUser, UUID trackerId) {
		WorkoutPlan plan = getLockedPlan(trackerId);
		plan.getDays().forEach(day -> day.getExercises().forEach(exercise -> exercise.setCompleted(false)));
		log.info("User {} reset workout completion in tracker {}", currentUser.getEmail(), trackerId);
	}

	private WorkoutPlan getLockedPlan(UUID trackerId) {
		return workoutPlanRepository.findLockedByTrackerId(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Workout plan not found"));
	}

	private WorkoutExercise findExercise(WorkoutPlan plan, String dayId, String exerciseId) {
		return plan.getDays().stream()
				.filter(day -> day.getKey().equals(dayId))
				.flatMap(day -> day.getExercises().stream())
				.filter(item -> item.getKey().equals(exerciseId))
				.findFirst()
				.orElseThrow(() -> new EntityNotFoundException("Workout exercise not found"));
	}

	private void assertNoImagesOnRemovedExercises(WorkoutPlan plan, WorkoutPlanUpsertRequestDto request) {
		Map<String, Set<String>> requested = request.days().stream().collect(Collectors.toMap(
				WorkoutPlanUpsertRequestDto.Day::id,
				day -> day.exercises().stream().map(WorkoutPlanUpsertRequestDto.Exercise::id).collect(Collectors.toSet())));
		for (WorkoutDay day : plan.getDays()) {
			Set<String> retained = requested.getOrDefault(day.getKey(), Set.of());
			for (WorkoutExercise exercise : day.getExercises()) {
				if (exercise.getImageUrl() != null && !retained.contains(exercise.getKey())) {
					throw new InvalidWorkoutException("Remove the image from exercise '%s' in day '%s' before removing it"
							.formatted(exercise.getName(), day.getName()));
				}
			}
		}
	}

	private void updateExercises(WorkoutDay day, WorkoutPlanUpsertRequestDto.Day request) {
		Map<String, WorkoutExercise> existing = day.getExercises().stream()
				.collect(Collectors.toMap(WorkoutExercise::getKey, Function.identity()));
		Set<String> retained = new HashSet<>();
		for (int i = 0; i < request.exercises().size(); i++) {
			var item = request.exercises().get(i);
			retained.add(item.id());
			WorkoutExercise exercise = existing.get(item.id());
			if (exercise == null) {
				exercise = new WorkoutExercise();
				exercise.setDay(day);
				exercise.setKey(item.id());
				day.getExercises().add(exercise);
			}
			exercise.setName(item.name());
			exercise.setSets(item.sets());
			exercise.setDescription(item.description());
			exercise.setSortOrder(i);
		}
		day.getExercises().removeIf(exercise -> !retained.contains(exercise.getKey()));
	}

	private void validateUniqueIds(WorkoutPlanUpsertRequestDto request) {
		Set<String> days = new HashSet<>();
		for (var day : request.days()) {
			if (!days.add(day.id())) {
				throw new InvalidWorkoutException("Duplicate workout day id: " + day.id());
			}
			Set<String> exercises = new HashSet<>();
			for (var exercise : day.exercises()) {
				if (!exercises.add(exercise.id())) {
					throw new InvalidWorkoutException("Duplicate exercise id in day " + day.id() + ": " + exercise.id());
				}
			}
		}
	}

	private WorkoutPlanResponseDto toResponse(WorkoutPlan plan) {
		return new WorkoutPlanResponseDto(plan.getTitle(), plan.getWeekLabel(), plan.getDays().stream()
				.sorted(Comparator.comparingInt(WorkoutDay::getSortOrder))
				.map(day -> new WorkoutPlanResponseDto.Day(day.getKey(), day.getLabel(), day.getName(), day.getFocus(),
						day.getExercises().stream()
								.sorted(Comparator.comparingInt(WorkoutExercise::getSortOrder))
								.map(item -> new WorkoutPlanResponseDto.Exercise(item.getKey(), item.getName(),
										item.getSets(), item.getDescription(), item.isCompleted(), item.getImageUrl()))
								.toList()))
				.toList());
	}
}
