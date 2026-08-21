package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.ReportCleaningIssueRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningIssueResponse;
import com.luna.aggarly.cleaning.entity.CleaningIssue;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.IssueSeverity;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningIssueRepository;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.impl.CleaningIssueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleaningIssueServiceTest {

    @Mock
    private CleaningIssueRepository issueRepository;

    @Mock
    private CleaningTaskRepository taskRepository;

    @Mock
    private CleaningMapper cleaningMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CleaningIssueServiceImpl issueService;

    private UUID taskId;
    private UUID cleanerId;
    private UUID hostId;
    private UUID propertyId;
    private UUID issueId;
    private CleaningTask sampleTask;
    private CleaningIssue sampleIssue;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        cleanerId = UUID.randomUUID();
        hostId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        issueId = UUID.randomUUID();

        sampleTask = CleaningTask.builder()
                .hostId(hostId)
                .assignedCleanerId(cleanerId)
                .propertyId(propertyId)
                .build();
        sampleTask.setId(taskId);

        sampleIssue = CleaningIssue.builder()
                .cleaningTask(sampleTask)
                .propertyId(propertyId)
                .reportedBy(cleanerId)
                .title("Broken window latch")
                .description("Cannot lock living room window")
                .severity(IssueSeverity.HIGH)
                .build();
        sampleIssue.setId(issueId);
    }

    @Test
    @DisplayName("reportIssue should persist issue and emit CleaningIssueReportedEvent")
    void testReportIssue() {
        ReportCleaningIssueRequest request = new ReportCleaningIssueRequest(
                "Broken window latch", "Cannot lock living room window", IssueSeverity.HIGH, null
        );

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(sampleTask));
        when(issueRepository.save(any(CleaningIssue.class))).thenReturn(sampleIssue);
        when(cleaningMapper.toResponse(sampleIssue)).thenReturn(new CleaningIssueResponse(
                issueId, taskId, propertyId, null, cleanerId, "Broken window latch", "Cannot lock living room window",
                IssueSeverity.HIGH, null, false, null, null, Instant.now()
        ));

        CleaningIssueResponse response = issueService.reportIssue(taskId, request, cleanerId);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Broken window latch");
        verify(issueRepository).save(any(CleaningIssue.class));
        verify(eventPublisher).publishEvent(any());
    }
}
