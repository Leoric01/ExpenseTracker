package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.food.dtos.MacroCalculationCommand;
import org.leoric.expensetracker.food.dtos.MacroTargets;
import org.springframework.stereotype.Service;

@Service
public interface MacroCalculationService {

	MacroTargets calculate(MacroCalculationCommand command);
}