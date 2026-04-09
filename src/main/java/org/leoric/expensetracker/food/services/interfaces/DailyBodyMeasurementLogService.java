package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.DailyBodyMeasurementLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyBodyMeasurementLogRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public interface DailyBodyMeasurementLogService {

	DailyBodyMeasurementLogResponseDto dailyBodyMeasurementLogUpsert(User currentUser, UUID trackerId, UpsertDailyBodyMeasurementLogRequestDto request);

	DailyBodyMeasurementLogResponseDto dailyBodyMeasurementLogFindByDate(User currentUser, UUID trackerId, LocalDate logDate);

	Page<DailyBodyMeasurementLogResponseDto> dailyBodyMeasurementLogFindAll(User currentUser, UUID trackerId, LocalDate from, LocalDate to, Pageable pageable);

	void dailyBodyMeasurementLogDeleteByDate(User currentUser, UUID trackerId, LocalDate logDate);
}