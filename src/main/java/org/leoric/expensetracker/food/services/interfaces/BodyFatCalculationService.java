package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.food.dtos.BodyFatCalculationCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface BodyFatCalculationService {

	BigDecimal calculate(BodyFatCalculationCommand command);

}