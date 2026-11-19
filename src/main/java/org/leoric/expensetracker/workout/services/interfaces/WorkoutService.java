package org.leoric.expensetracker.workout.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanResponseDto;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanUpsertRequestDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface WorkoutService {
	WorkoutPlanResponseDto workoutFind(User currentUser, UUID trackerId);
	WorkoutPlanResponseDto workoutUpsert(User currentUser, UUID trackerId, WorkoutPlanUpsertRequestDto request);
	void workoutSetCompletion(User currentUser, UUID trackerId, String dayId, String exerciseId, boolean completed);
	void workoutReset(User currentUser, UUID trackerId);
	WorkoutPlanResponseDto workoutUploadImage(User currentUser, UUID trackerId, String dayId, String exerciseId, MultipartFile file);
	void workoutDeleteImage(User currentUser, UUID trackerId, String dayId, String exerciseId);
}
