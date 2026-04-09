package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.food.dtos.WeeklyCheckinCalculationCommand;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinSnapshot;
import org.springframework.stereotype.Service;

@Service
public interface WeeklyCheckinCalculationService {

	WeeklyCheckinSnapshot calculate(WeeklyCheckinCalculationCommand command);

}