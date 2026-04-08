package org.leoric.expensetracker.institution.repositories;

import org.leoric.expensetracker.institution.models.Institution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

	List<Institution> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	Page<Institution> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	@Query("""
			SELECT i FROM Institution i
			WHERE i.expenseTracker.id = :trackerId
			AND i.active = true
			AND (
				LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(i.institutionType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Institution> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID expenseTrackerId, String name);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}