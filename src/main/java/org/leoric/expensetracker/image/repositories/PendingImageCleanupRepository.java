package org.leoric.expensetracker.image.repositories;

import org.leoric.expensetracker.image.models.PendingImageCleanup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PendingImageCleanupRepository extends JpaRepository<PendingImageCleanup, Long> {
	List<PendingImageCleanup> findByCleanedFalse();
	long countByCleanedFalse();
	Optional<PendingImageCleanup> findFirstBySourceUrl(String sourceUrl);
}
