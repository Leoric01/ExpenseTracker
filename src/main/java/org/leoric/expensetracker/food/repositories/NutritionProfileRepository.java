package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.NutritionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NutritionProfileRepository extends JpaRepository<NutritionProfile, UUID> {

	Optional<NutritionProfile> findByExpenseTrackerId(UUID expenseTrackerId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}