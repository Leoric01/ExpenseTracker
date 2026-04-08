package org.leoric.expensetracker.institution.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.institution.dto.CreateInstitutionRequestDto;
import org.leoric.expensetracker.institution.dto.InstitutionResponseDto;
import org.leoric.expensetracker.institution.dto.UpdateInstitutionRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public interface InstitutionService {

	InstitutionResponseDto institutionCreate(User currentUser, UUID trackerId, CreateInstitutionRequestDto request);

	InstitutionResponseDto institutionFindById(User currentUser, UUID trackerId, UUID institutionId);

	Page<InstitutionResponseDto> institutionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	InstitutionResponseDto institutionUpdate(User currentUser, UUID trackerId, UUID institutionId, UpdateInstitutionRequestDto request);

	void institutionDeactivate(User currentUser, UUID trackerId, UUID institutionId);

	InstitutionResponseDto institutionUploadIcon(User currentUser, UUID trackerId, UUID institutionId, MultipartFile icon, String iconColor);

	InstitutionResponseDto institutionDeleteIcon(User currentUser, UUID trackerId, UUID institutionId);
}