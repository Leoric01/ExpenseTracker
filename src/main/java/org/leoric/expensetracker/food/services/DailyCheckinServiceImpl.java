package org.leoric.expensetracker.food.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.DailyBodyMeasurementLogResponseDto;
import org.leoric.expensetracker.food.dtos.DailyCheckinResponseDto;
import org.leoric.expensetracker.food.dtos.DailyNutritionLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyBodyMeasurementLogRequestDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyCheckinRequestDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyNutritionLogRequestDto;
import org.leoric.expensetracker.food.services.interfaces.DailyBodyMeasurementLogService;
import org.leoric.expensetracker.food.services.interfaces.DailyCheckinService;
import org.leoric.expensetracker.food.services.interfaces.DailyNutritionLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyCheckinServiceImpl implements DailyCheckinService {

	private final DailyNutritionLogService dailyNutritionLogService;
	private final DailyBodyMeasurementLogService dailyBodyMeasurementLogService;

	@Override
	@Transactional
	public DailyCheckinResponseDto dailyCheckinUpsert(User currentUser, UUID trackerId, UpsertDailyCheckinRequestDto request) {
		DailyNutritionLogResponseDto nutritionLog = dailyNutritionLogService.dailyNutritionLogUpsert(
				currentUser,
				trackerId,
				new UpsertDailyNutritionLogRequestDto(
						request.logDate(),
						request.weightKg(),
						request.caloriesKcal(),
						request.proteinG(),
						request.fatG(),
						request.carbsG(),
						request.notes()
				)
		);

		DailyBodyMeasurementLogResponseDto bodyMeasurementLog = null;
		if (hasBodyMeasurements(request)) {
			bodyMeasurementLog = dailyBodyMeasurementLogService.dailyBodyMeasurementLogUpsert(
					currentUser,
					trackerId,
					new UpsertDailyBodyMeasurementLogRequestDto(
							request.logDate(),
							request.waistCm(),
							request.neckCm(),
							request.hipCm()
					)
			);
		}

		log.info("User {} upserted daily checkin for date {} in tracker '{}'",
		         currentUser.getEmail(), request.logDate(), trackerId);

		return new DailyCheckinResponseDto(nutritionLog, bodyMeasurementLog);
	}

	private boolean hasBodyMeasurements(UpsertDailyCheckinRequestDto request) {
		return request.waistCm() != null || request.neckCm() != null || request.hipCm() != null;
	}
}