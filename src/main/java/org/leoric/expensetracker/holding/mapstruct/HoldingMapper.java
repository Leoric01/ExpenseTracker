package org.leoric.expensetracker.holding.mapstruct;

import org.leoric.expensetracker.holding.dto.HoldingResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface HoldingMapper {

	@Mapping(source = "account.id", target = "accountId")
	@Mapping(source = "account.name", target = "accountName")
	@Mapping(source = "account.accountType", target = "accountType")
	@Mapping(source = "account.institution.id", target = "institutionId")
	@Mapping(source = "account.institution.name", target = "institutionName")
	@Mapping(source = "account.institution.institutionType", target = "institutionType")
	@Mapping(source = "asset.id", target = "assetId")
	@Mapping(source = "asset.code", target = "assetCode")
	@Mapping(source = "asset.name", target = "assetName")
	@Mapping(source = "asset.assetType", target = "assetType")
	@Mapping(source = "asset.scale", target = "assetScale")
	HoldingResponseDto toResponse(Holding entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}