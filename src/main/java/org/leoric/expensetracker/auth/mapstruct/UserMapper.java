package org.leoric.expensetracker.auth.mapstruct;

import org.leoric.expensetracker.auth.dto.UserInfoResponseDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {UserExpenseTrackerRoleMapper.class})
public interface UserMapper {

	@Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToNames")
	UserInfoResponseDto userToUserInfoResponseDto(User user);

	@Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToNames")
	UserResponseFullDto userToUserResponseFull(User user);

	@Mapping(target = "userExpenseTrackerRoles", ignore = true)
	@Mapping(target = "roles", ignore = true)
	@Mapping(target = "password", ignore = true)
	@Mapping(target = "navbarFavourites", ignore = true)
	@Mapping(target = "lastModifiedDate", ignore = true)
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "expenseTrackers", ignore = true)
	@Mapping(target = "enabled", ignore = true)
	@Mapping(target = "email", ignore = true)
	@Mapping(target = "createdDate", ignore = true)
	@Mapping(target = "accountLocked", ignore = true)
	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
	void updateUserFromDto(UserProfileUpdateDto dto, @MappingTarget User user);

	@Named("mapRolesToNames")
	static List<String> mapRolesToNames(List<Role> roles) {
		if (roles == null) return Collections.emptyList();
		return roles.stream().map(Role::getName).collect(Collectors.toList());
	}

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	default Instant map(OffsetDateTime odt) {
		return odt == null ? null : odt.toInstant();
	}
}