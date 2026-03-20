package org.leoric.expensetracker.expensetracker.mapstruct;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponseDto;
import org.leoric.expensetracker.expensetracker.models.ExpenseTrackerAccessRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ExpenseTrackerAccessRequestMapper {

	@Mapping(source = "expenseTracker.id", target = "expenseTrackerId")
	@Mapping(source = "expenseTracker.name", target = "expenseTrackerName")
	@Mapping(source = "user.id", target = "userId")
	@Mapping(source = "user", target = "userFullName", qualifiedByName = "userToFullName")
	@Mapping(source = "user.email", target = "userEmail")
	@Mapping(source = "expenseTrackerAccessRequestStatus", target = "status")
	@Mapping(source = "expenseTrackerAccessRequestType", target = "type")
	@Mapping(source = "approvedBy", target = "approvedByFullName", qualifiedByName = "userToFullName")
	@Mapping(source = "invitedBy", target = "invitedByFullName", qualifiedByName = "userToFullName")
	ExpenseTrackerAccessRequestResponseDto toResponse(ExpenseTrackerAccessRequest entity);

	@Named("userToFullName")
	static String userToFullName(User user) {
		return user == null ? null : user.getFullname();
	}

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}