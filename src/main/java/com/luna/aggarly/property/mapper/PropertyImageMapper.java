package com.luna.aggarly.property.mapper;

import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.entity.PropertyImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PropertyImageMapper {

    @Mapping(target = "isCover", expression = "java(image.isCover())")
    PropertyImageResponse toResponse(PropertyImage image);
}
