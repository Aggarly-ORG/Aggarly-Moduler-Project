package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.AssignCleanerRequest;
import com.luna.aggarly.cleaning.dto.request.CompleteInspectionRequest;
import com.luna.aggarly.cleaning.dto.request.CreateCleaningTaskRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningTaskResponse;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import com.luna.aggarly.cleaning.exceptions.InvalidCleaningStatusTransitionException;
import com.luna.aggarly.cleaning.exceptions.UnauthorizedCleanerAccessException;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.impl.CleaningTaskServiceImpl;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleaningTaskServiceTest {

    @Mock
    private CleaningTaskRepository taskRepository;

    @Mock
    private CleaningChecklistService checklistService;

    @Mock
    private PropertyService propertyService;

    @Mock
    private CleaningMapper cleaningMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CleaningTaskServiceImpl cleaningTaskService;

    private UUID propertyId;
    private UUID hostId;
    private UUID cleanerId;
    private UUID taskId;
    private CleaningTask sampleTask;
    private PropertyResponse sampleProperty;

    @BeforeEach
    void setUp() {
        propertyId = UUID.randomUUID();
        hostId = UUID.randomUUID();
        cleanerId = UUID.randomUUID();
        taskId = UUID.randomUUID();

        sampleTask = CleaningTask.builder()
                .propertyId(propertyId)
                .hostId(hostId)
                .status(CleaningStatus.PENDING)
                .priority(CleaningPriority.NORMAL)
                .taskType(CleaningTaskType.TURNOVER)
                .scheduledDate(LocalDate.now().plusDays(1))
                .estimatedDurationMinutes(120)
                .build();
        sampleTask.setId(taskId);

        sampleProperty = new PropertyResponse(
                propertyId, hostId, "Beach Villa", "Nice place", null, 4, 2, 2,
                null, null, null, null, null, null, 0, null, null, null
        );
    }

    @Test
    @DisplayName("createCleaningTask should persist task and auto-generate room checklists")
    void testCreateCleaningTask() {
        CreateCleaningTaskRequest request = new CreateCleaningTaskRequest(
                propertyId, null, null, CleaningPriority.HIGH, CleaningTaskType.TURNOVER,
                LocalDate.now().plusDays(2), LocalTime.of(11, 0), 120, "Prepare for VIP"
        );

        when(propertyService.getPropertyById(propertyId)).thenReturn(sampleProperty);
        when(cleaningMapper.toEntity(request)).thenReturn(sampleTask);
        when(taskRepository.save(any(CleaningTask.class))).thenReturn(sampleTask);
        when(cleaningMapper.toResponse(sampleTask)).thenReturn(new CleaningTaskResponse(
                taskId, propertyId, null, hostId, null, CleaningStatus.PENDING,
                CleaningPriority.HIGH, CleaningTaskType.TURNOVER, LocalDate.now().plusDays(2),
                LocalTime.of(11, 0), 120, null, null, null, null, null, null, null, null, null, null
        ));

        CleaningTaskResponse response = cleaningTaskService.createCleaningTask(request, hostId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(taskId);
        verify(checklistService).generateDefaultChecklistsForTask(sampleTask, 2, 2);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("assignCleaner should transition status to SCHEDULED")
    void testAssignCleaner() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(CleaningTask.class))).thenReturn(sampleTask);
        when(cleaningMapper.toResponse(sampleTask)).thenReturn(new CleaningTaskResponse(
                taskId, propertyId, null, hostId, cleanerId, CleaningStatus.SCHEDULED,
                CleaningPriority.NORMAL, CleaningTaskType.TURNOVER, LocalDate.now().plusDays(1),
                null, 120, null, null, null, null, null, null, null, null, null, null
        ));

        AssignCleanerRequest request = new AssignCleanerRequest(cleanerId, LocalDate.now().plusDays(1), LocalTime.of(10, 0));
        CleaningTaskResponse response = cleaningTaskService.assignCleaner(taskId, request, hostId);

        assertThat(response.status()).isEqualTo(CleaningStatus.SCHEDULED);
        assertThat(sampleTask.getAssignedCleanerId()).isEqualTo(cleanerId);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("startCleaning by unauthorized user should throw UnauthorizedCleanerAccessException")
    void testStartCleaningUnauthorized() {
        UUID strangerId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> cleaningTaskService.startCleaning(taskId, strangerId))
                .isInstanceOf(UnauthorizedCleanerAccessException.class);
    }

    @Test
    @DisplayName("startCleaning should transition status to IN_PROGRESS and record timestamp")
    void testStartCleaningSuccess() {
        sampleTask.setStatus(CleaningStatus.SCHEDULED);
        sampleTask.setAssignedCleanerId(cleanerId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(CleaningTask.class))).thenReturn(sampleTask);
        when(cleaningMapper.toResponse(sampleTask)).thenReturn(new CleaningTaskResponse(
                taskId, propertyId, null, hostId, cleanerId, CleaningStatus.IN_PROGRESS,
                CleaningPriority.NORMAL, CleaningTaskType.TURNOVER, LocalDate.now().plusDays(1),
                null, 120, null, null, null, null, null, null, null, null, null, null
        ));

        CleaningTaskResponse response = cleaningTaskService.startCleaning(taskId, cleanerId);

        assertThat(response.status()).isEqualTo(CleaningStatus.IN_PROGRESS);
        assertThat(sampleTask.getActualStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeCleaning from invalid state should throw InvalidCleaningStatusTransitionException")
    void testCompleteCleaningInvalidState() {
        sampleTask.setStatus(CleaningStatus.PENDING);
        sampleTask.setAssignedCleanerId(cleanerId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> cleaningTaskService.completeCleaning(taskId, cleanerId, "Done"))
                .isInstanceOf(InvalidCleaningStatusTransitionException.class);
    }

    @Test
    @DisplayName("inspectCleaning should update rating and feedback")
    void testInspectCleaning() {
        sampleTask.setStatus(CleaningStatus.COMPLETED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(CleaningTask.class))).thenReturn(sampleTask);
        when(cleaningMapper.toResponse(sampleTask)).thenReturn(new CleaningTaskResponse(
                taskId, propertyId, null, hostId, cleanerId, CleaningStatus.INSPECTED,
                CleaningPriority.NORMAL, CleaningTaskType.TURNOVER, LocalDate.now().plusDays(1),
                null, 120, null, null, null, "Great job", 5, null, null, null, null, null
        ));

        CompleteInspectionRequest request = new CompleteInspectionRequest(5, "Great job");
        CleaningTaskResponse response = cleaningTaskService.inspectCleaning(taskId, request, hostId);

        assertThat(response.status()).isEqualTo(CleaningStatus.INSPECTED);
        assertThat(sampleTask.getRatingByHost()).isEqualTo(5);
    }
}
