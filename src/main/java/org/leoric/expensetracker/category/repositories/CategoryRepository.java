package org.leoric.expensetracker.category.repositories;

import org.leoric.expensetracker.category.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByExpenseTrackerId(UUID expenseTrackerId);

	List<Category> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	List<Category> findByExpenseTrackerIdAndParentIsNull(UUID expenseTrackerId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}