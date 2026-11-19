package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
	Optional<Role> findByName(String role);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM Role r WHERE r.name = :name")
	Optional<Role> findByNameForUpdate(@Param("name") String name);
}