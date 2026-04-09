package org.leoric.expensetracker.food.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.dtos.BodyFatCalculationCommand;
import org.leoric.expensetracker.food.dtos.DailyBodyMeasurementLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyBodyMeasurementLogRequestDto;
import org.leoric.expensetracker.food.models.DailyBodyMeasurementLog;
import org.leoric.expensetracker.food.models.GoalPlan;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.models.constants.BodyFatSource;
import org.leoric.expensetracker.food.repositories.DailyBodyMeasurementLogRepository;
import org.leoric.expensetracker.food.services.interfaces.BodyFatCalculationService;
import org.leoric.expensetracker.food.services.interfaces.DailyBodyMeasurementLogService;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyBodyMeasurementLogServiceImpl implements DailyBodyMeasurementLogService {

	private final DailyBodyMeasurementLogRepository dailyBodyMeasurementLogRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;
	private final BodyFatCalculationService bodyFatCalculationService;

	@Override
	@Transactional
	public DailyBodyMeasurementLogResponseDto dailyBodyMeasurementLogUpsert(User currentUser, UUID trackerId, UpsertDailyBodyMeasurementLogRequestDto request) {
		ExpenseTracker tracker = nutritionDomainLookupService.getTrackerOrThrow(trackerId);
		GoalPlan goalPlan = nutritionDomainLookupService.getActiveGoalPlanOrThrow(trackerId);
		NutritionProfile profile = nutritionDomainLookupService.getNutritionProfileOrThrow(trackerId);

		BigDecimal calculatedBodyFatPercent = bodyFatCalculationService.calculate(
				new BodyFatCalculationCommand(
						profile.getBiologicalSex(),
						profile.getHeightCm(),
						request.waistCm(),
						request.neckCm(),
						request.hipCm()
				)
		);

		DailyBodyMeasurementLog logEntry = dailyBodyMeasurementLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, request.logDate())
				.orElseGet(() -> DailyBodyMeasurementLog.builder()
						.expenseTracker(tracker)
						.goalPlan(goalPlan)
						.logDate(request.logDate())
						.build());

		logEntry.setGoalPlan(goalPlan);
		logEntry.setWaistCm(request.waistCm());
		logEntry.setNeckCm(request.neckCm());
		logEntry.setHipCm(request.hipCm());
		logEntry.setCalculatedBodyFatPercent(calculatedBodyFatPercent);
		logEntry.setBodyFatSource(BodyFatSource.CIRCUMFERENCE);

		logEntry = dailyBodyMeasurementLogRepository.save(logEntry);

		log.info("User {} upserted daily body measurement log for date {} in tracker '{}'",
		         currentUser.getEmail(), request.logDate(), tracker.getName());

		return toResponse(logEntry);
	}

	@Override
	@Transactional(readOnly = true)
	public DailyBodyMeasurementLogResponseDto dailyBodyMeasurementLogFindByDate(User currentUser, UUID trackerId, LocalDate logDate) {
		DailyBodyMeasurementLog logEntry = dailyBodyMeasurementLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, logDate)
				.orElseThrow(() -> new EntityNotFoundException(
						"Daily body measurement log not found for tracker id %s and date %s".formatted(trackerId, logDate)));
		return toResponse(logEntry);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DailyBodyMeasurementLogResponseDto> dailyBodyMeasurementLogFindAll(User currentUser, UUID trackerId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from == null || to == null) {
			return dailyBodyMeasurementLogRepository.findByExpenseTrackerId(trackerId, pageable)
					.map(this::toResponse);
		}

		List<DailyBodyMeasurementLogResponseDto> results = dailyBodyMeasurementLogRepository
				.findByExpenseTrackerIdAndLogDateBetweenOrderByLogDateAsc(trackerId, from, to)
				.stream()
				.map(this::toResponse)
				.toList();

		return new PageImpl<>(results, pageable, results.size());
	}

	@Override
	@Transactional
	public void dailyBodyMeasurementLogDeleteByDate(User currentUser, UUID trackerId, LocalDate logDate) {
		DailyBodyMeasurementLog logEntry = dailyBodyMeasurementLogRepository.findByExpenseTrackerIdAndLogDate(trackerId, logDate)
				.orElseThrow(() -> new EntityNotFoundException(
						"Daily body measurement log not found for tracker id %s and date %s".formatted(trackerId, logDate)));

		dailyBodyMeasurementLogRepository.delete(logEntry);

		log.info("User {} deleted daily body measurement log for date {} in tracker '{}'",
		         currentUser.getEmail(), logDate, logEntry.getExpenseTracker().getName());
	}

	private DailyBodyMeasurementLogResponseDto toResponse(DailyBodyMeasurementLog logEntry) {
		return new DailyBodyMeasurementLogResponseDto(
				logEntry.getId(),
				logEntry.getExpenseTracker().getId(),
				logEntry.getGoalPlan().getId(),
				logEntry.getLogDate(),
				logEntry.getWaistCm(),
				logEntry.getNeckCm(),
				logEntry.getHipCm(),
				logEntry.getCalculatedBodyFatPercent(),
				logEntry.getBodyFatSource(),
				logEntry.getCreatedDate(),
				logEntry.getLastModifiedDate()
		);
	}
}