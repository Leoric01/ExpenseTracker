package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.NavbarFavourite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NavbarFavouriteRepository extends JpaRepository<NavbarFavourite, UUID> {
}