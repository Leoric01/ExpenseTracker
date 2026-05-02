package org.leoric.expensetracker.category.repositories;

import org.leoric.expensetracker.category.models.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	Page<Category> findByExpenseTrackerIdAndActiveTrueAndParentIsNull(UUID expenseTrackerId, Pageable pageable);

	Page<Category> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	List<Category> findByExpenseTrackerIdAndActiveTrueAndIdIn(UUID expenseTrackerId, Collection<UUID> ids);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	@Query("""
			SELECT DISTINCT c FROM Category c
			LEFT JOIN c.children ch
			WHERE c.expenseTracker.id = :trackerId
			AND c.active = true
			AND c.parent IS NULL
			AND (
				LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(c.categoryKind AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (ch.active = true AND LOWER(ch.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (ch.active = true AND LOWER(CAST(ch.categoryKind AS string)) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<Category> findRootsByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	boolean existsByExpenseTrackerIdAndParentIdAndNameIgnoreCase(UUID expenseTrackerId, UUID parentId, String name);

	@Query("""
			SELECT c FROM Category c
			WHERE c.expenseTracker.id = :trackerId
			AND c.active = true
			AND (
				LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(c.categoryKind AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Category> findActiveByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);
}