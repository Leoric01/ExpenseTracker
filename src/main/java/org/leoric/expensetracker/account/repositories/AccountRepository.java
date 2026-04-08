package org.leoric.expensetracker.account.repositories;

import org.leoric.expensetracker.account.models.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

	Page<Account> findByInstitutionIdAndActiveTrue(UUID institutionId, Pageable pageable);

	@Query("""
			SELECT a FROM Account a
			WHERE a.institution.expenseTracker.id = :trackerId
			AND a.active = true
			""")
	Page<Account> findByExpenseTrackerIdAndActiveTrue(@Param("trackerId") UUID trackerId, Pageable pageable);

	@Query("""
			SELECT a FROM Account a
			WHERE a.institution.expenseTracker.id = :trackerId
			AND a.active = true
			AND (
				LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(a.accountType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(a.institution.name) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Account> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

	@Modifying
	@Query("DELETE FROM Account a WHERE a.institution.expenseTracker.id = :trackerId")
	void deleteByExpenseTrackerId(@Param("trackerId") UUID trackerId);
}