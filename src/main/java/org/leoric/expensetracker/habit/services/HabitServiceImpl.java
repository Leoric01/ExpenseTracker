package org.leoric.expensetracker.habit.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.habit.dtos.HabitAgendaItemDto;
import org.leoric.expensetracker.habit.dtos.HabitCompletionStateDto;
import org.leoric.expensetracker.habit.dtos.HabitDayBlockOverviewDto;
import org.leoric.expensetracker.habit.dtos.HabitDayOverviewDto;
import org.leoric.expensetracker.habit.dtos.HabitResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitScheduleSlotRequestDto;
import org.leoric.expensetracker.habit.dtos.HabitUpsertRequestDto;
import org.leoric.expensetracker.habit.dtos.HabitWeekOverviewResponseDto;
import org.leoric.expensetracker.habit.mapstruct.HabitMapper;
import org.leoric.expensetracker.habit.models.Habit;
import org.leoric.expensetracker.habit.models.HabitCompletion;
import org.leoric.expensetracker.habit.models.HabitScheduleSlot;
import org.leoric.expensetracker.habit.models.constants.DayBlock;
import org.leoric.expensetracker.habit.models.constants.HabitType;
import org.leoric.expensetracker.habit.repositories.HabitAgendaProjection;
import org.leoric.expensetracker.habit.repositories.HabitCompletionRepository;
import org.leoric.expensetracker.habit.repositories.HabitRepository;
import org.leoric.expensetracker.habit.repositories.HabitScheduleSlotRepository;
import org.leoric.expensetracker.habit.services.interfaces.HabitService;
import org.leoric.expensetracker.handler.exceptions.DuplicateHabitNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidHabitException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HabitServiceImpl implements HabitService {

	private final HabitRepository habitRepository;
	private final HabitScheduleSlotRepository habitScheduleSlotRepository;
	private final HabitCompletionRepository habitCompletionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final HabitMapper habitMapper;

	private static Habit apply(Habit a, Habit b) {
		return a;
	}

	@Override
	@Transactional
	public HabitResponseDto habitCreate(User currentUser, UUID trackerId, HabitUpsertRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		validateHabitRequest(request);

		if (habitRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, request.name())) {
			throw new DuplicateHabitNameException(
					"Habit with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		Habit habit = Habit.builder()
				.expenseTracker(tracker)
				.name(request.name().trim())
				.description(normalizeNullable(request.description()))
				.habitType(request.habitType())
				.expectedMinutes(request.expectedMinutes())
				.validFrom(request.validFrom())
				.validTo(request.validTo())
				.active(request.active())
				.sortOrder(defaultIfNull(request.sortOrder()))
				.satisfactionScore(defaultIfNull(request.satisfactionScore()))
				.utilityScore(defaultIfNull(request.utilityScore()))
				.estimatedPrice(request.estimatedPrice())
				.build();

		habit = habitRepository.save(habit);
		saveScheduleSlots(habit, request.scheduleSlots());

		log.info("User {} created habit '{}' in tracker '{}'",
		         currentUser.getEmail(), habit.getName(), tracker.getName());

		return toHabitResponse(habit);
	}

	@Override
	@Transactional(readOnly = true)
	public HabitResponseDto habitFindById(User currentUser, UUID trackerId, UUID habitId) {
		Habit habit = getHabitOrThrow(habitId);
		assertHabitBelongsToTracker(habit, trackerId);
		return toHabitResponse(habit);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<HabitResponseDto> habitFindAll(
			User currentUser,
			UUID trackerId,
			String search,
			Boolean active,
			Pageable pageable
	) {
		Page<Habit> habits = habitRepository.findAllByExpenseTrackerIdWithFilters(
				trackerId,
				normalizeSearch(search),
				active,
				pageable
		);

		Map<UUID, List<HabitScheduleSlot>> slotsByHabitId = loadSlotsByHabitIds(
				habits.getContent().stream().map(Habit::getId).toList()
		);

		return habits.map(habit -> habitMapper.toResponseDto(
				habit,
				slotsByHabitId.getOrDefault(habit.getId(), List.of())
		));
	}

	@Override
	@Transactional
	public HabitResponseDto habitUpdate(User currentUser, UUID trackerId, UUID habitId, HabitUpsertRequestDto request) {
		Habit habit = getHabitOrThrow(habitId);
		assertHabitBelongsToTracker(habit, trackerId);
		validateHabitRequest(request);

		if (habitRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(
				trackerId,
				request.name(),
				habitId
		)) {
			throw new DuplicateHabitNameException(
					"Habit with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		habit.setName(request.name().trim());
		habit.setDescription(normalizeNullable(request.description()));
		habit.setHabitType(request.habitType());
		habit.setExpectedMinutes(request.expectedMinutes());
		habit.setValidFrom(request.validFrom());
		habit.setValidTo(request.validTo());
		habit.setActive(request.active());
		habit.setSortOrder(defaultIfNull(request.sortOrder()));
		habit.setSatisfactionScore(defaultIfNull(request.satisfactionScore()));
		habit.setUtilityScore(defaultIfNull(request.utilityScore()));
		habit.setEstimatedPrice(request.estimatedPrice());

		habit = habitRepository.save(habit);

		habitScheduleSlotRepository.deleteByHabitId(habit.getId());
		saveScheduleSlots(habit, request.scheduleSlots());

		log.info("User {} updated habit '{}' in tracker '{}'",
		         currentUser.getEmail(), habit.getName(), trackerId);

		return toHabitResponse(habit);
	}

	@Override
	@Transactional
	public void habitDeactivate(User currentUser, UUID trackerId, UUID habitId) {
		Habit habit = getHabitOrThrow(habitId);
		assertHabitBelongsToTracker(habit, trackerId);

		habit.setActive(false);
		habitRepository.save(habit);

		log.info("User {} deactivated habit '{}' in tracker '{}'",
		         currentUser.getEmail(), habit.getName(), trackerId);
	}

	@Override
	@Transactional
	public void habitActivate(User currentUser, UUID trackerId, UUID habitId) {
		Habit habit = getHabitOrThrow(habitId);
		assertHabitBelongsToTracker(habit, trackerId);

		habit.setActive(true);
		habitRepository.save(habit);

		log.info("User {} activated habit '{}' in tracker '{}'",
		         currentUser.getEmail(), habit.getName(), trackerId);
	}

	@Override
	@Transactional
	public void habitDelete(User currentUser, UUID trackerId, UUID habitId) {
		Habit habit = getHabitOrThrow(habitId);
		assertHabitBelongsToTracker(habit, trackerId);

		habit.setDeleted(true);
		habit.setActive(false);
		habitRepository.save(habit);

		log.info("User {} soft deleted habit '{}' in tracker '{}'",
		         currentUser.getEmail(), habit.getName(), trackerId);
	}

	@Override
	@Transactional(readOnly = true)
	public HabitDayOverviewDto habitFindAgendaForDate(User currentUser, UUID trackerId, LocalDate date) {
		List<HabitAgendaProjection> rows = habitScheduleSlotRepository.findDailyAgendaProjection(
				trackerId,
				date,
				date.getDayOfWeek()
		);

		return buildDayOverview(date, rows);
	}

	@Override
	@Transactional(readOnly = true)
	public HabitWeekOverviewResponseDto habitFindWeekOverview(User currentUser, UUID trackerId, LocalDate weekStart) {
		LocalDate normalizedWeekStart = normalizeWeekStart(weekStart);
		LocalDate weekEnd = normalizedWeekStart.plusDays(6);

		List<HabitScheduleSlot> slots = habitScheduleSlotRepository.findAllActiveByExpenseTrackerIdAndDateRange(
				trackerId,
				normalizedWeekStart,
				weekEnd
		);

		if (slots.isEmpty()) {
			return new HabitWeekOverviewResponseDto(
					normalizedWeekStart,
					weekEnd,
					buildEmptyWeek(normalizedWeekStart)
			);
		}

		Map<UUID, Habit> habitsById = slots.stream()
				.map(HabitScheduleSlot::getHabit)
				.collect(Collectors.toMap(Habit::getId, Function.identity(), HabitServiceImpl::apply));

		List<HabitCompletion> completions = habitCompletionRepository.findAllByHabitIdsAndDateRange(
				habitsById.keySet(),
				normalizedWeekStart,
				weekEnd
		);

		Map<UUID, Map<LocalDate, HabitCompletion>> completionsByHabitAndDate = completions.stream()
				.collect(Collectors.groupingBy(
						c -> c.getHabit().getId(),
						Collectors.toMap(HabitCompletion::getDate, Function.identity())
				));

		List<HabitDayOverviewDto> days = new ArrayList<>();

		for (int i = 0; i < 7; i++) {
			LocalDate date = normalizedWeekStart.plusDays(i);
			DayOfWeek dayOfWeek = date.getDayOfWeek();

			List<HabitScheduleSlot> daySlots = slots.stream()
					.filter(slot -> slot.getDayOfWeek() == dayOfWeek)
					.toList();

			days.add(buildDayOverview(date, daySlots, completionsByHabitAndDate));
		}

		return new HabitWeekOverviewResponseDto(normalizedWeekStart, weekEnd, days);
	}

	private HabitResponseDto toHabitResponse(Habit habit) {
		List<HabitScheduleSlot> scheduleSlots = habitScheduleSlotRepository
				.findByHabitIdOrderByDayOfWeekAscDayBlockAscSortOrderAsc(habit.getId());

		return habitMapper.toResponseDto(habit, scheduleSlots);
	}

	private void saveScheduleSlots(Habit habit, List<HabitScheduleSlotRequestDto> slotRequests) {
		List<HabitScheduleSlot> slots = slotRequests.stream()
				.map(slot -> HabitScheduleSlot.builder()
						.habit(habit)
						.dayOfWeek(slot.dayOfWeek())
						.dayBlock(slot.dayBlock())
						.sortOrder(defaultIfNull(slot.sortOrder()))
						.build())
				.toList();

		habitScheduleSlotRepository.saveAll(slots);
	}

	private Map<UUID, List<HabitScheduleSlot>> loadSlotsByHabitIds(List<UUID> habitIds) {
		if (habitIds.isEmpty()) {
			return Map.of();
		}

		return habitScheduleSlotRepository.findAllByHabitIds(habitIds).stream()
				.collect(Collectors.groupingBy(slot -> slot.getHabit().getId()));
	}

	private HabitDayOverviewDto buildDayOverview(LocalDate date, List<HabitAgendaProjection> rows) {
		Map<UUID, HabitAgendaAccumulator> itemsByHabitId = new LinkedHashMap<>();

		for (HabitAgendaProjection row : rows) {
			HabitAgendaAccumulator acc = itemsByHabitId.computeIfAbsent(
					row.getHabitId(),
					_ -> new HabitAgendaAccumulator(
							row.getHabitId(),
							row.getHabitName(),
							row.getHabitDescription(),
							row.getHabitType(),
							row.getExpectedMinutes(),
							row.getHabitSortOrder(),
							row.getSatisfactionScore(),
							row.getUtilityScore(),
							row.getEstimatedPrice(),
							new ArrayList<>(),
							row.getCompletionId() == null
									? null
									: new HabitCompletionStateDto(
									row.getCompletionId(),
									row.getCompletionDate(),
									row.getCompletionStatus(),
									row.getCompletionNote(),
									defaultIfNull(row.getCompletionSatisfactionScore()),
									defaultIfNull(row.getCompletionExecutionScore()),
									row.getCompletionActualPrice(),
									toOffsetDateTime(row.getCompletedAt())
							)
					)
			);

			acc.dayBlocks().add(row.getDayBlock());
		}

		Map<DayBlock, List<HabitAgendaItemDto>> grouped = itemsByHabitId.values().stream()
				.flatMap(acc -> acc.dayBlocks().stream().map(block -> Map.entry(block, toAgendaItemDto(acc))))
				.collect(Collectors.groupingBy(
						Map.Entry::getKey,
						LinkedHashMap::new,
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())
				));

		List<HabitDayBlockOverviewDto> blocks = Arrays.stream(DayBlock.values())
				.map(block -> new HabitDayBlockOverviewDto(
						block,
						grouped.getOrDefault(block, List.of()).stream()
								.sorted(Comparator.comparing(HabitAgendaItemDto::sortOrder).thenComparing(HabitAgendaItemDto::name))
								.toList()
				))
				.toList();

		return new HabitDayOverviewDto(date, date.getDayOfWeek(), blocks);
	}

	private HabitDayOverviewDto buildDayOverview(
			LocalDate date,
			List<HabitScheduleSlot> daySlots,
			Map<UUID, Map<LocalDate, HabitCompletion>> completionsByHabitAndDate
	) {
		Map<DayBlock, List<HabitAgendaItemDto>> grouped = new LinkedHashMap<>();

		for (DayBlock block : DayBlock.values()) {
			grouped.put(block, new ArrayList<>());
		}

		Map<UUID, List<HabitScheduleSlot>> slotsByHabitId = daySlots.stream()
				.collect(Collectors.groupingBy(slot -> slot.getHabit().getId()));

		for (Map.Entry<UUID, List<HabitScheduleSlot>> entry : slotsByHabitId.entrySet()) {
			Habit habit = entry.getValue().getFirst().getHabit();
			HabitCompletion completion = completionsByHabitAndDate
					.getOrDefault(habit.getId(), Map.of())
					.get(date);

			HabitCompletionStateDto completionDto = completion == null
					? null
					: new HabitCompletionStateDto(
					completion.getId(),
					completion.getDate(),
					completion.getStatus(),
					completion.getNote(),
					completion.getSatisfactionScore(),
					completion.getExecutionScore(),
					completion.getActualPrice(),
					toOffsetDateTime(completion.getCompletedAt())
			);

			List<DayBlock> dayBlocks = entry.getValue().stream()
					.map(HabitScheduleSlot::getDayBlock)
					.distinct()
					.sorted()
					.toList();

			HabitAgendaItemDto item = new HabitAgendaItemDto(
					habit.getId(),
					habit.getName(),
					habit.getDescription(),
					habit.getHabitType(),
					habit.getExpectedMinutes(),
					habit.getSortOrder(),
					habit.getSatisfactionScore(),
					habit.getUtilityScore(),
					habit.getEstimatedPrice(),
					dayBlocks,
					completionDto
			);

			for (DayBlock block : dayBlocks) {
				grouped.get(block).add(item);
			}
		}

		List<HabitDayBlockOverviewDto> blocks = grouped.entrySet().stream()
				.map(entry -> new HabitDayBlockOverviewDto(
						entry.getKey(),
						entry.getValue().stream()
								.sorted(Comparator.comparing(HabitAgendaItemDto::sortOrder).thenComparing(HabitAgendaItemDto::name))
								.toList()
				))
				.toList();

		return new HabitDayOverviewDto(date, date.getDayOfWeek(), blocks);
	}

	private List<HabitDayOverviewDto> buildEmptyWeek(LocalDate weekStart) {
		List<HabitDayOverviewDto> days = new ArrayList<>();

		for (int i = 0; i < 7; i++) {
			LocalDate date = weekStart.plusDays(i);

			List<HabitDayBlockOverviewDto> blocks = Arrays.stream(DayBlock.values())
					.map(block -> new HabitDayBlockOverviewDto(block, List.of()))
					.toList();

			days.add(new HabitDayOverviewDto(date, date.getDayOfWeek(), blocks));
		}

		return days;
	}

	private HabitAgendaItemDto toAgendaItemDto(HabitAgendaAccumulator acc) {
		List<DayBlock> distinctBlocks = acc.dayBlocks().stream().distinct().sorted().toList();

		return new HabitAgendaItemDto(
				acc.habitId(),
				acc.name(),
				acc.description(),
				acc.habitType(),
				acc.expectedMinutes(),
				acc.sortOrder(),
				acc.satisfactionScore(),
				acc.utilityScore(),
				acc.estimatedPrice(),
				distinctBlocks,
				acc.completion()
		);
	}

	private void validateHabitRequest(HabitUpsertRequestDto request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new InvalidHabitException("Habit name must not be blank");
		}

		if (request.habitType() == null) {
			throw new InvalidHabitException("Habit type must not be null");
		}

		if (request.expectedMinutes() == null || request.expectedMinutes() <= 0) {
			throw new InvalidHabitException("Expected minutes must be greater than 0");
		}

		if (request.validFrom() == null) {
			throw new InvalidHabitException("Valid from must not be null");
		}

		if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
			throw new InvalidHabitException("Valid to must not be before valid from");
		}

		if (request.scheduleSlots() == null || request.scheduleSlots().isEmpty()) {
			throw new InvalidHabitException("Habit must contain at least one schedule slot");
		}

		Set<String> uniqueSlots = new HashSet<>();
		for (HabitScheduleSlotRequestDto slot : request.scheduleSlots()) {
			if (slot.dayOfWeek() == null || slot.dayBlock() == null) {
				throw new InvalidHabitException("Schedule slot dayOfWeek and dayBlock must not be null");
			}

			String key = slot.dayOfWeek().name() + "_" + slot.dayBlock().name();
			if (!uniqueSlots.add(key)) {
				throw new InvalidHabitException(
						"Duplicate schedule slot for %s %s".formatted(slot.dayOfWeek(), slot.dayBlock()));
			}
		}
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException(
						"Expense tracker with id '%s' not found".formatted(trackerId)));
	}

	private Habit getHabitOrThrow(UUID habitId) {
		return habitRepository.findById(habitId)
				.filter(habit -> !habit.isDeleted())
				.orElseThrow(() -> new EntityNotFoundException(
						"Habit with id '%s' not found".formatted(habitId)));
	}

	private void assertHabitBelongsToTracker(Habit habit, UUID trackerId) {
		if (!habit.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException(
					"Habit with id '%s' was not found in tracker '%s'".formatted(habit.getId(), trackerId));
		}
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();
		return trimmed.isBlank() ? null : trimmed;
	}

	private String normalizeSearch(String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		return search.trim();
	}

	private LocalDate normalizeWeekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	private Integer defaultIfNull(Integer value) {
		return value == null ? (Integer) 0 : value;
	}

	private OffsetDateTime toOffsetDateTime(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private record HabitAgendaAccumulator(
			UUID habitId,
			String name,
			String description,
			HabitType habitType,
			Integer expectedMinutes,
			Integer sortOrder,
			Integer satisfactionScore,
			Integer utilityScore,
			Integer estimatedPrice,
			List<DayBlock> dayBlocks,
			HabitCompletionStateDto completion
	) {
	}
}