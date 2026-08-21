package com.luna.aggarly.property.mapper;

import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.entity.Amenity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AmenityMapper {

    AmenityResponse toResponse(Amenity amenity);
}
