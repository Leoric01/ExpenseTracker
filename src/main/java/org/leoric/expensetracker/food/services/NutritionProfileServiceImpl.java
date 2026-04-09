package org.leoric.expensetracker.food.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.dtos.NutritionProfileResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertNutritionProfileRequestDto;
import org.leoric.expensetracker.food.models.NutritionProfile;
import org.leoric.expensetracker.food.repositories.NutritionProfileRepository;
import org.leoric.expensetracker.food.services.interfaces.NutritionDomainLookupService;
import org.leoric.expensetracker.food.services.interfaces.NutritionProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NutritionProfileServiceImpl implements NutritionProfileService {

	private final NutritionProfileRepository nutritionProfileRepository;
	private final NutritionDomainLookupService nutritionDomainLookupService;

	@Override
	@Transactional
	public NutritionProfileResponseDto nutritionProfileUpsert(User currentUser, UUID trackerId, UpsertNutritionProfileRequestDto request) {
		ExpenseTracker tracker = nutritionDomainLookupService.getTrackerOrThrow(trackerId);

		NutritionProfile profile = nutritionProfileRepository.findByExpenseTrackerId(trackerId)
				.orElseGet(() -> NutritionProfile.builder()
						.expenseTracker(tracker)
						.build());

		profile.setPreferredUnitSystem(request.preferredUnitSystem());
		profile.setBiologicalSex(request.biologicalSex());
		profile.setHeightCm(request.heightCm());
		profile.setActivityMultiplier(request.activityMultiplier());
		profile.setBodyFatAutoCalculationEnabled(request.bodyFatAutoCalculationEnabled());

		profile = nutritionProfileRepository.save(profile);

		log.info("User {} upserted nutrition profile in tracker '{}'", currentUser.getEmail(), tracker.getName());

		return toResponse(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NutritionProfileResponseDto nutritionProfileFind(User currentUser, UUID trackerId) {
		NutritionProfile profile = nutritionDomainLookupService.getNutritionProfileOrThrow(trackerId);
		return toResponse(profile);
	}

	private NutritionProfileResponseDto toResponse(NutritionProfile profile) {
		return new NutritionProfileResponseDto(
				profile.getId(),
				profile.getExpenseTracker().getId(),
				profile.getPreferredUnitSystem(),
				profile.getBiologicalSex(),
				profile.getHeightCm(),
				profile.getActivityMultiplier(),
				profile.isBodyFatAutoCalculationEnabled(),
				profile.getCreatedDate(),
				profile.getLastModifiedDate()
		);
	}
}