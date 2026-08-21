package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.AssignCleanerRequest;
import com.luna.aggarly.cleaning.dto.request.CompleteInspectionRequest;
import com.luna.aggarly.cleaning.dto.request.CreateCleaningTaskRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskSummaryResponse;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface CleaningTaskService {

    CleaningTaskResponse createCleaningTask(CreateCleaningTaskRequest request, UUID hostId);

    CleaningTaskResponse scheduleTurnoverForBooking(UUID bookingId, UUID propertyId, UUID hostId, LocalDate checkoutDate);

    void cancelTurnoverForBooking(UUID bookingId);

    CleaningTaskResponse assignCleaner(UUID taskId, AssignCleanerRequest request, UUID hostId);

    CleaningTaskResponse startCleaning(UUID taskId, UUID cleanerId);

    CleaningTaskResponse completeCleaning(UUID taskId, UUID cleanerId, String cleanerNotes);

    CleaningTaskResponse skipCleaning(UUID taskId, UUID hostId, String reason);

    CleaningTaskResponse inspectCleaning(UUID taskId, CompleteInspectionRequest request, UUID hostId);

    CleaningTaskResponse getCleaningTaskById(UUID taskId, UUID currentUserId);

    Page<CleaningTaskSummaryResponse> getHostTasks(UUID hostId, CleaningStatus status, UUID propertyId, Pageable pageable);

    Page<CleaningTaskSummaryResponse> getCleanerTasks(UUID cleanerId, CleaningStatus status, Pageable pageable);

    Page<CleaningTaskSummaryResponse> getPropertyCleaningHistory(UUID propertyId, Pageable pageable);
}
