package org.leoric.expensetracker.asset.repositories;

import org.leoric.expensetracker.asset.models.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

	Optional<Asset> findByCodeIgnoreCase(String code);

	@Query("""
			SELECT a FROM Asset a
			WHERE UPPER(a.code) IN :codesUpper
			""")
	Set<Asset> findAllByCodeUpperIn(@Param("codesUpper") Set<String> codesUpper);

	boolean existsByCodeIgnoreCase(String code);

	Page<Asset> findByActiveTrue(Pageable pageable);

	@Query("""
			SELECT a FROM Asset a
			WHERE a.active = true
			AND (
				LOWER(a.code) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(a.assetType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<Asset> findByActiveTrueWithSearch(@Param("search") String search, Pageable pageable);
}