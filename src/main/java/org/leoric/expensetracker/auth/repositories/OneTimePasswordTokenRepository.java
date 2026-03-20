package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.OneTimePasswordToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OneTimePasswordTokenRepository extends JpaRepository<OneTimePasswordToken, UUID> {

	void deleteByUserId(UUID userId);
}