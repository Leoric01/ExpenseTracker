package org.leoric.expensetracker.holding.services.interfaces;

import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public interface HoldingSummaryBuilder {

	HoldingSummaryResponseDto buildSummary(Holding holding, Instant from, Instant to);
}