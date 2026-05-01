package org.leoric.expensetracker.holding.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.holding.dto.CreateHoldingRequestDto;
import org.leoric.expensetracker.holding.dto.HoldingLiteResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public interface HoldingService {

	HoldingResponseDto holdingCreate(User currentUser, UUID trackerId, CreateHoldingRequestDto request);

	HoldingResponseDto holdingFindById(User currentUser, UUID trackerId, UUID holdingId);

	Page<HoldingResponseDto> holdingFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	List<HoldingLiteResponseDto> holdingFindAllLite(User currentUser, UUID trackerId);

	void holdingDeactivate(User currentUser, UUID trackerId, UUID holdingId);

	HoldingSummaryResponseDto holdingSummary(User currentUser, UUID trackerId, UUID holdingId, Instant from, Instant to);
}