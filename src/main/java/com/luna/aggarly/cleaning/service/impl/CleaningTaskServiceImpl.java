package com.luna.aggarly.cleaning.service.impl;

import com.luna.aggarly.cleaning.dto.request.AssignCleanerRequest;
import com.luna.aggarly.cleaning.dto.request.CompleteInspectionRequest;
import com.luna.aggarly.cleaning.dto.request.CreateCleaningTaskRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskSummaryResponse;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import com.luna.aggarly.cleaning.event.CleaningCompletedEvent;
import com.luna.aggarly.cleaning.event.CleaningStatusChangedEvent;
import com.luna.aggarly.cleaning.event.CleaningTaskCreatedEvent;
import com.luna.aggarly.cleaning.exceptions.CleaningTaskNotFoundException;
import com.luna.aggarly.cleaning.exceptions.InvalidCleaningStatusTransitionException;
import com.luna.aggarly.cleaning.exceptions.UnauthorizedCleanerAccessException;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.CleaningChecklistService;
import com.luna.aggarly.cleaning.service.CleaningTaskService;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningTaskServiceImpl implements CleaningTaskService {

    private final CleaningTaskRepository taskRepository;
    private final CleaningChecklistService checklistService;
    private final PropertyService propertyService;
    private final CleaningMapper cleaningMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CleaningTaskResponse createCleaningTask(CreateCleaningTaskRequest request, UUID hostId) {
        log.info("Creating cleaning task for property={}, host={}", request.propertyId(), hostId);

        PropertyResponse property = propertyService.getPropertyById(request.propertyId());

        CleaningTask task = cleaningMapper.toEntity(request);
        task.setHostId(hostId);
        task.setStatus(request.assignedCleanerId() != null ? CleaningStatus.SCHEDULED : CleaningStatus.PENDING);
        task.setPriority(request.priority() != null ? request.priority() : CleaningPriority.NORMAL);
        task.setTaskType(request.taskType() != null ? request.taskType() : CleaningTaskType.TURNOVER);
        task.setEstimatedDurationMinutes(request.estimatedDurationMinutes() != null ? request.estimatedDurationMinutes() : 120);

        CleaningTask saved = taskRepository.save(task);

        // Auto-generate checklists
        checklistService.generateDefaultChecklistsForTask(saved, property.bedrooms(), property.bathrooms());

        eventPublisher.publishEvent(new CleaningTaskCreatedEvent(
                this,
                saved.getId(),
                saved.getPropertyId(),
                saved.getBookingId(),
                saved.getHostId(),
                saved.getAssignedCleanerId(),
                saved.getTaskType(),
                saved.getPriority(),
                saved.getScheduledDate()
        ));

        log.info("Cleaning task created: id={}, status={}", saved.getId(), saved.getStatus());
        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningTaskResponse scheduleTurnoverForBooking(UUID bookingId, UUID propertyId, UUID hostId, LocalDate checkoutDate) {
        log.info("Auto-scheduling turnover cleaning for booking={}, property={}, checkoutDate={}",
                bookingId, propertyId, checkoutDate);

        Optional<CleaningTask> existing = taskRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            log.info("Turnover task already exists for booking {}: id={}", bookingId, existing.get().getId());
            return cleaningMapper.toResponse(existing.get());
        }

        PropertyResponse property = propertyService.getPropertyById(propertyId);

        CleaningTask task = CleaningTask.builder()
                .propertyId(propertyId)
                .bookingId(bookingId)
                .hostId(hostId)
                .status(CleaningStatus.PENDING)
                .priority(CleaningPriority.HIGH)
                .taskType(CleaningTaskType.TURNOVER)
                .scheduledDate(checkoutDate)
                .estimatedDurationMinutes(120)
                .cleanerNotes("Auto-scheduled checkout turnover for booking " + bookingId)
                .build();

        CleaningTask saved = taskRepository.save(task);
        checklistService.generateDefaultChecklistsForTask(saved, property.bedrooms(), property.bathrooms());

        eventPublisher.publishEvent(new CleaningTaskCreatedEvent(
                this,
                saved.getId(),
                saved.getPropertyId(),
                saved.getBookingId(),
                saved.getHostId(),
                saved.getAssignedCleanerId(),
                saved.getTaskType(),
                saved.getPriority(),
                saved.getScheduledDate()
        ));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void cancelTurnoverForBooking(UUID bookingId) {
        taskRepository.findByBookingId(bookingId).ifPresent(task -> {
            if (task.getStatus() == CleaningStatus.PENDING || task.getStatus() == CleaningStatus.SCHEDULED) {
                CleaningStatus prev = task.getStatus();
                task.setStatus(CleaningStatus.CANCELLED);
                taskRepository.save(task);
                log.info("Cancelled cleaning task {} for cancelled booking {}", task.getId(), bookingId);
                eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                        this, task.getId(), task.getPropertyId(), task.getHostId(),
                        task.getAssignedCleanerId(), prev, CleaningStatus.CANCELLED));
            }
        });
    }

    @Override
    @Transactional
    public CleaningTaskResponse assignCleaner(UUID taskId, AssignCleanerRequest request, UUID hostId) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        if (!task.getHostId().equals(hostId)) {
            throw new UnauthorizedCleanerAccessException("Only the property host can assign cleaners");
        }

        CleaningStatus prev = task.getStatus();
        task.setAssignedCleanerId(request.cleanerId());
        if (request.scheduledDate() != null) {
            task.setScheduledDate(request.scheduledDate());
        }
        if (request.scheduledStartTime() != null) {
            task.setScheduledStartTime(request.scheduledStartTime());
        }

        if (task.getStatus() == CleaningStatus.PENDING) {
            task.setStatus(CleaningStatus.SCHEDULED);
        }

        CleaningTask saved = taskRepository.save(task);
        log.info("Assigned cleaner {} to cleaning task {}", request.cleanerId(), taskId);

        eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                this, saved.getId(), saved.getPropertyId(), saved.getHostId(),
                saved.getAssignedCleanerId(), prev, saved.getStatus()));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningTaskResponse startCleaning(UUID taskId, UUID cleanerId) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        boolean isAuthorized = task.getHostId().equals(cleanerId)
                || (task.getAssignedCleanerId() != null && task.getAssignedCleanerId().equals(cleanerId));

        if (!isAuthorized) {
            throw new UnauthorizedCleanerAccessException("You are not authorized to start this cleaning task");
        }

        if (task.getStatus() != CleaningStatus.SCHEDULED && task.getStatus() != CleaningStatus.PENDING) {
            throw new InvalidCleaningStatusTransitionException(task.getStatus(), CleaningStatus.IN_PROGRESS);
        }

        CleaningStatus prev = task.getStatus();
        task.setStatus(CleaningStatus.IN_PROGRESS);
        task.setActualStartedAt(Instant.now());
        if (task.getAssignedCleanerId() == null) {
            task.setAssignedCleanerId(cleanerId);
        }

        CleaningTask saved = taskRepository.save(task);
        log.info("Cleaning task {} started by cleaner {}", taskId, cleanerId);

        eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                this, saved.getId(), saved.getPropertyId(), saved.getHostId(),
                saved.getAssignedCleanerId(), prev, CleaningStatus.IN_PROGRESS));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningTaskResponse completeCleaning(UUID taskId, UUID cleanerId, String cleanerNotes) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        boolean isAuthorized = task.getHostId().equals(cleanerId)
                || (task.getAssignedCleanerId() != null && task.getAssignedCleanerId().equals(cleanerId));

        if (!isAuthorized) {
            throw new UnauthorizedCleanerAccessException("You are not authorized to complete this cleaning task");
        }

        if (task.getStatus() != CleaningStatus.IN_PROGRESS) {
            throw new InvalidCleaningStatusTransitionException(task.getStatus(), CleaningStatus.COMPLETED);
        }

        CleaningStatus prev = task.getStatus();
        task.setStatus(CleaningStatus.COMPLETED);
        task.setActualCompletedAt(Instant.now());
        if (cleanerNotes != null) {
            task.setCleanerNotes(cleanerNotes);
        }

        CleaningTask saved = taskRepository.save(task);
        log.info("Cleaning task {} completed by cleaner {}", taskId, cleanerId);

        eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                this, saved.getId(), saved.getPropertyId(), saved.getHostId(),
                saved.getAssignedCleanerId(), prev, CleaningStatus.COMPLETED));

        eventPublisher.publishEvent(new CleaningCompletedEvent(
                this,
                saved.getId(),
                saved.getPropertyId(),
                saved.getBookingId(),
                saved.getHostId(),
                saved.getAssignedCleanerId()
        ));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningTaskResponse skipCleaning(UUID taskId, UUID hostId, String reason) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        if (!task.getHostId().equals(hostId)) {
            throw new UnauthorizedCleanerAccessException("Only the property host can skip a cleaning task");
        }

        if (task.getStatus() == CleaningStatus.COMPLETED || task.getStatus() == CleaningStatus.INSPECTED) {
            throw new InvalidCleaningStatusTransitionException(task.getStatus(), CleaningStatus.SKIPPED);
        }

        CleaningStatus prev = task.getStatus();
        task.setStatus(CleaningStatus.SKIPPED);
        task.setHostFeedback(reason);

        CleaningTask saved = taskRepository.save(task);
        log.info("Cleaning task {} skipped by host {}: reason={}", taskId, hostId, reason);

        eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                this, saved.getId(), saved.getPropertyId(), saved.getHostId(),
                saved.getAssignedCleanerId(), prev, CleaningStatus.SKIPPED));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningTaskResponse inspectCleaning(UUID taskId, CompleteInspectionRequest request, UUID hostId) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        if (!task.getHostId().equals(hostId)) {
            throw new UnauthorizedCleanerAccessException("Only the property host can inspect a cleaning task");
        }

        if (task.getStatus() != CleaningStatus.COMPLETED) {
            throw new InvalidCleaningStatusTransitionException(task.getStatus(), CleaningStatus.INSPECTED);
        }

        CleaningStatus prev = task.getStatus();
        task.setStatus(CleaningStatus.INSPECTED);
        if (request.ratingByHost() != null) {
            task.setRatingByHost(request.ratingByHost());
        }
        if (request.hostFeedback() != null) {
            task.setHostFeedback(request.hostFeedback());
        }

        CleaningTask saved = taskRepository.save(task);
        log.info("Cleaning task {} inspected by host {}: rating={}", taskId, hostId, request.ratingByHost());

        eventPublisher.publishEvent(new CleaningStatusChangedEvent(
                this, saved.getId(), saved.getPropertyId(), saved.getHostId(),
                saved.getAssignedCleanerId(), prev, CleaningStatus.INSPECTED));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CleaningTaskResponse getCleaningTaskById(UUID taskId, UUID currentUserId) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        return cleaningMapper.toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CleaningTaskSummaryResponse> getHostTasks(UUID hostId, CleaningStatus status, UUID propertyId, Pageable pageable) {
        return taskRepository.findByHostFilter(hostId, status, propertyId, pageable)
                .map(cleaningMapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CleaningTaskSummaryResponse> getCleanerTasks(UUID cleanerId, CleaningStatus status, Pageable pageable) {
        return taskRepository.findByCleanerFilter(cleanerId, status, pageable)
                .map(cleaningMapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CleaningTaskSummaryResponse> getPropertyCleaningHistory(UUID propertyId, Pageable pageable) {
        return taskRepository.findByPropertyIdOrderByScheduledDateDesc(propertyId, pageable)
                .map(cleaningMapper::toSummaryResponse);
    }
}
