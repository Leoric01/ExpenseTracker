package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantServiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantServiceTagRepository extends JpaRepository<RestaurantServiceTag, UUID> {
	List<RestaurantServiceTag> findByExpenseTrackerIdOrderByNameAsc(UUID trackerId);
	Optional<RestaurantServiceTag> findByIdAndExpenseTrackerId(UUID id, UUID trackerId);
	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID trackerId, String name);
}
