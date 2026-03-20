package org.leoric.expensetracker.auth.mapstruct;

import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface UserExpenseTrackerRoleMapper {

	@Mapping(source = "role.name", target = "roleName")
	@Mapping(source = "expenseTracker.id", target = "expenseTrackerId")
	@Mapping(source = "expenseTracker.name", target = "expenseTrackerName")
	UserExpenseTrackerRoleDto toDto(UserExpenseTrackerRole entity);

	record UserExpenseTrackerRoleDto(
			Integer id,
			String roleName,
			UUID expenseTrackerId,
			String expenseTrackerName
	) {
	}
}