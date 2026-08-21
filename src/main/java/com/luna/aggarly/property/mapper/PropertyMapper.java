package com.luna.aggarly.property.mapper;

import com.luna.aggarly.property.dto.request.AddressRequest;
import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.response.AddressResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.entity.Address;
import com.luna.aggarly.property.entity.Property;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AmenityMapper.class, PropertyImageMapper.class})
public interface PropertyMapper {

    @Mapping(target = "hostId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "avgRating", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "amenities", ignore = true)
    @Mapping(target = "address", ignore = true) // Will set manually to handle bidirectional link
    Property toEntity(CreatePropertyRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "property", ignore = true)
    Address toAddressEntity(AddressRequest request);

    PropertyResponse toResponse(Property property);

    AddressResponse toAddressResponse(Address address);
}
