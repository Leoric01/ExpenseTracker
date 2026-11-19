package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.RoleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, Long> {
	List<RoleChangeAudit> findTop5ByTargetIdOrderByChangedAtDesc(UUID targetId);
}
