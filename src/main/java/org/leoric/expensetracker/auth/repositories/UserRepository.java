package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String userEmail);

	boolean existsByEmail(String email);

	Page<User> findByEmailContainingIgnoreCase(String search, Pageable pageable);

	List<User> findByEmailContainingIgnoreCase(String search);
}