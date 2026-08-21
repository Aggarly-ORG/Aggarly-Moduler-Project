package com.luna.aggarly.cleaning.service.impl;

import com.luna.aggarly.cleaning.dto.response.CleaningPhotoResponse;
import com.luna.aggarly.cleaning.entity.CleaningPhoto;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningPhotoType;
import com.luna.aggarly.cleaning.exceptions.CleaningTaskNotFoundException;
import com.luna.aggarly.cleaning.exceptions.UnauthorizedCleanerAccessException;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningPhotoRepository;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.CleaningPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningPhotoServiceImpl implements CleaningPhotoService {

    private final CleaningPhotoRepository photoRepository;
    private final CleaningTaskRepository taskRepository;
    private final CleaningMapper cleaningMapper;

    @Override
    @Transactional
    public CleaningPhotoResponse uploadCleaningPhoto(UUID taskId, String roomName, CleaningPhotoType photoType, String objectKey, UUID uploadedBy) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        boolean isAuthorized = task.getHostId().equals(uploadedBy)
                || (task.getAssignedCleanerId() != null && task.getAssignedCleanerId().equals(uploadedBy));

        if (!isAuthorized) {
            throw new UnauthorizedCleanerAccessException("You are not authorized to upload photos for this cleaning task");
        }

        CleaningPhoto photo = CleaningPhoto.builder()
                .cleaningTask(task)
                .roomName(roomName)
                .photoType(photoType != null ? photoType : CleaningPhotoType.AFTER)
                .objectKey(objectKey)
                .uploadedBy(uploadedBy)
                .build();

        CleaningPhoto saved = photoRepository.save(photo);
        log.info("Cleaning photo uploaded: id={}, task={}, type={}", saved.getId(), taskId, saved.getPhotoType());
        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningPhotoResponse> getPhotosByTaskId(UUID taskId) {
        List<CleaningPhoto> photos = photoRepository.findByCleaningTaskId(taskId);
        return cleaningMapper.toPhotoResponseList(photos);
    }
}
