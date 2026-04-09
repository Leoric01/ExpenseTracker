package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.food.dtos.ObservedTdeeCalculationCommand;
import org.leoric.expensetracker.food.dtos.TdeeCalculationCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface TdeeCalculationService {

	BigDecimal calculateBaselineTdee(TdeeCalculationCommand command);

	BigDecimal calculateObservedTdee(ObservedTdeeCalculationCommand command);
}