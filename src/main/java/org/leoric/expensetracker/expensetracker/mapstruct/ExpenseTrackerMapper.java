package org.leoric.expensetracker.expensetracker.mapstruct;

import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMemberDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMineResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ExpenseTrackerMapper {

	@Mapping(source = "createdByOwner.fullname", target = "ownerFullName")
	@Mapping(source = "userExpenseTrackerRoles", target = "members")
	@Mapping(source = "preferredDisplayAsset.id", target = "preferredDisplayAssetId")
	@Mapping(source = "preferredDisplayAsset.code", target = "preferredDisplayAssetCode")
	ExpenseTrackerResponseDto toResponse(ExpenseTracker entity);

	@Mapping(source = "entity.createdByOwner.fullname", target = "ownerFullName")
	@Mapping(source = "entity.userExpenseTrackerRoles", target = "members")
	@Mapping(source = "entity.preferredDisplayAsset.id", target = "preferredDisplayAssetId")
	@Mapping(source = "entity.preferredDisplayAsset.code", target = "preferredDisplayAssetCode")
	@Mapping(source = "role", target = "role")
	ExpenseTrackerMineResponseDto toMineResponse(ExpenseTracker entity, String role);

	@Mapping(source = "user.id", target = "userId")
	@Mapping(source = "user.fullname", target = "fullName")
	@Mapping(source = "user.email", target = "email")
	@Mapping(source = "role.name", target = "role")
	@Mapping(source = "createdDate", target = "memberSince")
	ExpenseTrackerMemberDto toMemberDto(UserExpenseTrackerRole role);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "preferredDisplayAsset", ignore = true)
	void updateFromDto(UpdateExpenseTrackerRequestDto dto, @MappingTarget ExpenseTracker entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}