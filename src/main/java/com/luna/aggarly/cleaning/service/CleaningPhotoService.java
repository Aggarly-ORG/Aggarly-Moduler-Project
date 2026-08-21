package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.response.CleaningPhotoResponse;
import com.luna.aggarly.cleaning.entity.enums.CleaningPhotoType;

import java.util.List;
import java.util.UUID;

public interface CleaningPhotoService {

    CleaningPhotoResponse uploadCleaningPhoto(UUID taskId, String roomName, CleaningPhotoType photoType, String objectKey, UUID uploadedBy);

    List<CleaningPhotoResponse> getPhotosByTaskId(UUID taskId);
}
