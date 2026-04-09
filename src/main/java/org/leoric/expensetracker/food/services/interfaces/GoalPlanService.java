package org.leoric.expensetracker.food.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.food.dtos.CreateGoalPlanRequestDto;
import org.leoric.expensetracker.food.dtos.GoalPlanResponseDto;
import org.leoric.expensetracker.food.dtos.UpdateGoalPlanRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface GoalPlanService {

	GoalPlanResponseDto goalPlanCreate(User currentUser, UUID trackerId, CreateGoalPlanRequestDto request);

	GoalPlanResponseDto goalPlanFindById(User currentUser, UUID trackerId, UUID goalPlanId);

	Page<GoalPlanResponseDto> goalPlanFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	GoalPlanResponseDto goalPlanUpdate(User currentUser, UUID trackerId, UUID goalPlanId, UpdateGoalPlanRequestDto request);

	GoalPlanResponseDto goalPlanActivate(User currentUser, UUID trackerId, UUID goalPlanId);

	void goalPlanDeactivate(User currentUser, UUID trackerId, UUID goalPlanId);
}