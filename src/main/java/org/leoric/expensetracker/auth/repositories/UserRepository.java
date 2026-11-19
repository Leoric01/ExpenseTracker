package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String userEmail);

	boolean existsByEmail(String email);

	Page<User> findByEmailContainingIgnoreCase(String search, Pageable pageable);
	Page<User> findByExpenseTrackersId(UUID trackerId, Pageable pageable);

	@Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r WHERE r.name = :role")
	long countUsersWithRole(@Param("role") String role);
}