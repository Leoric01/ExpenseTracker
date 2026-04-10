package org.leoric.expensetracker.habit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.habit.dtos.HabitCompletionResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitCompletionUpsertRequestDto;
import org.leoric.expensetracker.habit.mapstruct.HabitMapper;
import org.leoric.expensetracker.habit.models.Habit;
import org.leoric.expensetracker.habit.models.HabitCompletion;
import org.leoric.expensetracker.habit.models.constants.CompletionStatus;
import org.leoric.expensetracker.habit.repositories.HabitCompletionRepository;
import org.leoric.expensetracker.habit.repositories.HabitRepository;
import org.leoric.expensetracker.habit.services.interfaces.HabitCompletionService;
import org.leoric.expensetracker.handler.exceptions.HabitNotFoundException;
import org.leoric.expensetracker.handler.exceptions.InvalidHabitCompletionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class HabitCompletionServiceImpl implements HabitCompletionService {

	private final HabitRepository habitRepository;
	private final HabitCompletionRepository habitCompletionRepository;
	private final HabitMapper habitMapper;

	@Override
	@Transactional
	public HabitCompletionResponseDto habitCompletionUpsert(
			User currentUser,
			UUID trackerId,
			HabitCompletionUpsertRequestDto request
	) {
		validateCompletionRequest(request);

		Habit habit = getHabitOrThrow(request.habitId());
		assertHabitBelongsToTracker(habit, trackerId);
		assertHabitAvailableForDate(habit, request.date());

		HabitCompletion completion = habitCompletionRepository.findByHabitIdAndDate(habit.getId(), request.date())
				.orElseGet(() -> HabitCompletion.builder()
						.habit(habit)
						.date(request.date())
						.build());

		completion.setStatus(request.status());
		completion.setNote(normalizeNullable(request.note()));
		completion.setSatisfactionScore(request.satisfactionScore() != null ? request.satisfactionScore() : 0);
		completion.setExecutionScore(request.executionScore() != null ? request.executionScore() : 0);
		completion.setActualPrice(request.actualPrice());
		completion.setCompletedAt(resolveCompletedAt(request.status()));

		completion = habitCompletionRepository.save(completion);

		log.info("User {} upserted completion for habit '{}' on {} with status {}",
		         currentUser.getEmail(), habit.getName(), request.date(), request.status());

		return habitMapper.toCompletionResponseDto(completion);
	}

	private void validateCompletionRequest(HabitCompletionUpsertRequestDto request) {
		if (request.habitId() == null) {
			throw new InvalidHabitCompletionException("Habit id must not be null");
		}

		if (request.date() == null) {
			throw new InvalidHabitCompletionException("Date must not be null");
		}

		if (request.status() == null) {
			throw new InvalidHabitCompletionException("Completion status must not be null");
		}
	}

	private Habit getHabitOrThrow(UUID habitId) {
		return habitRepository.findById(habitId)
				.filter(habit -> !habit.isDeleted())
				.orElseThrow(() -> new HabitNotFoundException(
						"Habit with id '%s' not found".formatted(habitId)));
	}

	private void assertHabitBelongsToTracker(Habit habit, UUID trackerId) {
		if (!habit.getExpenseTracker().getId().equals(trackerId)) {
			throw new HabitNotFoundException(
					"Habit with id '%s' was not found in tracker '%s'".formatted(habit.getId(), trackerId));
		}
	}

	private void assertHabitAvailableForDate(Habit habit, LocalDate date) {
		if (!habit.isActive()) {
			throw new InvalidHabitCompletionException(
					"Habit with id '%s' is not active".formatted(habit.getId()));
		}

		if (date.isBefore(habit.getValidFrom())) {
			throw new InvalidHabitCompletionException(
					"Completion date '%s' is before validFrom '%s'".formatted(date, habit.getValidFrom()));
		}

		if (habit.getValidTo() != null && date.isAfter(habit.getValidTo())) {
			throw new InvalidHabitCompletionException(
					"Completion date '%s' is after validTo '%s'".formatted(date, habit.getValidTo()));
		}
	}

	private Instant resolveCompletedAt(CompletionStatus status) {
		return status == CompletionStatus.DONE ? Instant.now() : null;
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();
		return trimmed.isBlank() ? null : trimmed;
	}
}