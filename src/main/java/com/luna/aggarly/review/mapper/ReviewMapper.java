package com.luna.aggarly.review.mapper;

import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.entity.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toResponse(Review review);
}
