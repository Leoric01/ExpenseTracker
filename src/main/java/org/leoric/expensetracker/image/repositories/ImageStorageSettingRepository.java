package org.leoric.expensetracker.image.repositories;

import org.leoric.expensetracker.image.models.ImageStorageSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageStorageSettingRepository extends JpaRepository<ImageStorageSetting, Integer> {}
