package org.leoric.expensetracker.asset.mapstruct;

import org.leoric.expensetracker.asset.dto.AssetResponseDto;
import org.leoric.expensetracker.asset.dto.UpdateAssetRequestDto;
import org.leoric.expensetracker.asset.models.Asset;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface AssetMapper {

	AssetResponseDto toResponse(Asset entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	void updateFromDto(UpdateAssetRequestDto dto, @MappingTarget Asset entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}