package org.leoric.expensetracker.category.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.category.dto.CategoryResponseDto;
import org.leoric.expensetracker.category.dto.CreateCategoryBulkRequestDto;
import org.leoric.expensetracker.category.dto.CreateCategoryRequestDto;
import org.leoric.expensetracker.category.dto.UpdateCategoryRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public interface CategoryService {

	CategoryResponseDto categoryCreate(User currentUser, UUID trackerId, CreateCategoryRequestDto request);

	List<CategoryResponseDto> categoryCreateBulk(User currentUser, UUID trackerId, List<CreateCategoryBulkRequestDto> request);

	CategoryResponseDto categoryFindById(User currentUser, UUID trackerId, UUID categoryId);

	Page<CategoryResponseDto> categoryFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	Page<CategoryResponseDto> categoryFindAllActive(User currentUser, UUID trackerId, String search, LocalDate dateFrom, LocalDate dateTo, Pageable pageable);

	CategoryResponseDto categoryUpdate(User currentUser, UUID trackerId, UUID categoryId, UpdateCategoryRequestDto request);

	void categoryDeactivate(User currentUser, UUID trackerId, UUID categoryId, boolean cascade);

	CategoryResponseDto categoryUploadIcon(User currentUser, UUID trackerId, UUID categoryId, MultipartFile icon, String iconColor);

	CategoryResponseDto categoryDeleteIcon(User currentUser, UUID trackerId, UUID categoryId);
}