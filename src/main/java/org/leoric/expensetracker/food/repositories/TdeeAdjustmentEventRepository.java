package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.TdeeAdjustmentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdeeAdjustmentEventRepository extends JpaRepository<TdeeAdjustmentEvent, UUID> {

	Page<TdeeAdjustmentEvent> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Optional<TdeeAdjustmentEvent> findByWeeklyCheckinId(UUID weeklyCheckinId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}