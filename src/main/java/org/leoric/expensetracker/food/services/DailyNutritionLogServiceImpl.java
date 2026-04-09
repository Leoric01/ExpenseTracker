package org.leoric.expensetracker.food.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.dtos.DailyNutritionLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyNutritionLogRequestDto;
import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.repositories.DailyNutritionLogRepository;
import org.leoric.expensetracker.food.services.interfaces.DailyNutritionLogService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyNutritionLogServiceImpl implements DailyNutritionLogService {

	private final DailyNutritionLogRepository dailyNutritionLogRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;

	@Override
	@Transactional
	public DailyNutritionLogResponseDto dailyNutritionLogUpsert(User currentUser, UUID trackerId, UpsertDailyNutritionLogRequestDto request) {
		ExpenseTracker tracker = nutritionDomainLookupService.getTrackerOrThrow(trackerId);
		GoalPlan goalPlan = nutritionDomainLookupService.getActiveGoalPlanOrThrow(trackerId);

		DailyNutritionLog logEntry = dailyNutritionLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, request.logDate())
				.orElseGet(() -> DailyNutritionLog.builder()
						.expenseTracker(tracker)
						.goalPlan(goalPlan)
						.logDate(request.logDate())
						.build());

		logEntry.setGoalPlan(goalPlan);
		logEntry.setWeightKg(request.weightKg());
		logEntry.setCaloriesKcal(request.caloriesKcal());
		logEntry.setProteinG(request.proteinG());
		logEntry.setFatG(request.fatG());
		logEntry.setCarbsG(request.carbsG());
		logEntry.setNotes(request.notes());

		logEntry = dailyNutritionLogRepository.save(logEntry);

		log.info("User {} upserted daily nutrition log for date {} in tracker '{}'",
		         currentUser.getEmail(), request.logDate(), tracker.getName());

		return toResponse(logEntry);
	}

	@Override
	@Transactional(readOnly = true)
	public DailyNutritionLogResponseDto dailyNutritionLogFindByDate(User currentUser, UUID trackerId, LocalDate logDate) {
		DailyNutritionLog logEntry = dailyNutritionLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, logDate)
				.orElseThrow(() -> new EntityNotFoundException(
						"Daily nutrition log not found for tracker id %s and date %s".formatted(trackerId, logDate)));
		return toResponse(logEntry);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DailyNutritionLogResponseDto> dailyNutritionLogFindAll(User currentUser, UUID trackerId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from == null || to == null) {
			return dailyNutritionLogRepository.findByExpenseTrackerId(trackerId, pageable)
					.map(this::toResponse);
		}

		List<DailyNutritionLogResponseDto> results = dailyNutritionLogRepository
				.findByExpenseTrackerIdAndLogDateBetweenOrderByLogDateAsc(trackerId, from, to)
				.stream()
				.map(this::toResponse)
				.toList();

		return new PageImpl<>(results, pageable, results.size());
	}

	@Override
	@Transactional
	public void dailyNutritionLogDeleteByDate(User currentUser, UUID trackerId, LocalDate logDate) {
		DailyNutritionLog logEntry = dailyNutritionLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, logDate)
				.orElseThrow(() -> new EntityNotFoundException(
						"Daily nutrition log not found for tracker id %s and date %s".formatted(trackerId, logDate)));

		dailyNutritionLogRepository.delete(logEntry);

		log.info("User {} deleted daily nutrition log for date {} in tracker '{}'",
		         currentUser.getEmail(), logDate, logEntry.getExpenseTracker().getName());
	}

	private DailyNutritionLogResponseDto toResponse(DailyNutritionLog logEntry) {
		return new DailyNutritionLogResponseDto(
				logEntry.getId(),
				logEntry.getExpenseTracker().getId(),
				logEntry.getGoalPlan().getId(),
				logEntry.getLogDate(),
				logEntry.getWeightKg(),
				logEntry.getCaloriesKcal(),
				logEntry.getProteinG(),
				logEntry.getFatG(),
				logEntry.getCarbsG(),
				logEntry.getNotes(),
				logEntry.getCreatedDate(),
				logEntry.getLastModifiedDate()
		);
	}
}