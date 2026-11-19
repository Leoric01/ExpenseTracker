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
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ExpenseTrackerMapper {

	@Mapping(source = "createdByOwner.fullname", target = "ownerFullName")
	@Mapping(source = "userExpenseTrackerRoles", target = "members", qualifiedByName = "members")
	@Mapping(source = "preferredDisplayAsset.id", target = "preferredDisplayAssetId")
	@Mapping(source = "preferredDisplayAsset.code", target = "preferredDisplayAssetCode")
	ExpenseTrackerResponseDto toResponse(ExpenseTracker entity);

	@Mapping(source = "entity.createdByOwner.fullname", target = "ownerFullName")
	@Mapping(source = "entity.userExpenseTrackerRoles", target = "members", qualifiedByName = "members")
	@Mapping(source = "entity.preferredDisplayAsset.id", target = "preferredDisplayAssetId")
	@Mapping(source = "entity.preferredDisplayAsset.code", target = "preferredDisplayAssetCode")
	@Mapping(source = "role", target = "role")
	@Mapping(source = "roles", target = "roles")
	ExpenseTrackerMineResponseDto toMineResponse(ExpenseTracker entity, String role, List<String> roles);

	@Named("members")
	default List<ExpenseTrackerMemberDto> members(List<UserExpenseTrackerRole> assignments) {
		Map<UUID, List<UserExpenseTrackerRole>> grouped = new LinkedHashMap<>();
		for (UserExpenseTrackerRole assignment : assignments)
			grouped.computeIfAbsent(assignment.getUser().getId(), _ -> new ArrayList<>()).add(assignment);
		return grouped.values().stream().map(items -> {
			UserExpenseTrackerRole first = items.getFirst();
			List<String> names = items.stream().map(a -> a.getRole().getName()).distinct()
					.sorted(Comparator.comparingInt(name -> name.equals("EXPENSETRACKER_OWNER") ? 0 : 1)).toList();
			return new ExpenseTrackerMemberDto(first.getUser().getId(), first.getUser().getFullname(),
					first.getUser().getEmail(), names.getFirst(), names, map(first.getCreatedDate()));
		}).toList();
	}

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "preferredDisplayAsset", ignore = true)
	void updateFromDto(UpdateExpenseTrackerRequestDto dto, @MappingTarget ExpenseTracker entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}