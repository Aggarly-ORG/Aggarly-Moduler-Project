package com.luna.aggarly.cleaning.service.impl;

import com.luna.aggarly.cleaning.dto.request.ReportCleaningIssueRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningIssueResponse;
import com.luna.aggarly.cleaning.entity.CleaningIssue;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.event.CleaningIssueReportedEvent;
import com.luna.aggarly.cleaning.exceptions.CleaningTaskNotFoundException;
import com.luna.aggarly.cleaning.exceptions.UnauthorizedCleanerAccessException;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningIssueRepository;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.CleaningIssueService;
import com.luna.aggarly.common.exceptions.AggarlyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningIssueServiceImpl implements CleaningIssueService {

    private final CleaningIssueRepository issueRepository;
    private final CleaningTaskRepository taskRepository;
    private final CleaningMapper cleaningMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CleaningIssueResponse reportIssue(UUID taskId, ReportCleaningIssueRequest request, UUID reportedBy) {
        CleaningTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CleaningTaskNotFoundException(taskId));

        boolean isAuthorized = task.getHostId().equals(reportedBy)
                || (task.getAssignedCleanerId() != null && task.getAssignedCleanerId().equals(reportedBy));

        if (!isAuthorized) {
            throw new UnauthorizedCleanerAccessException("You are not authorized to report issues for this cleaning task");
        }

        CleaningIssue issue = CleaningIssue.builder()
                .cleaningTask(task)
                .propertyId(task.getPropertyId())
                .bookingId(task.getBookingId())
                .reportedBy(reportedBy)
                .title(request.title())
                .description(request.description())
                .severity(request.severity())
                .photoKeys(request.photoKeys())
                .resolved(false)
                .build();

        CleaningIssue saved = issueRepository.save(issue);
        log.warn("Cleaning issue reported: id={}, task={}, title={}, severity={}",
                saved.getId(), taskId, saved.getTitle(), saved.getSeverity());

        // Publish event for notifications & maintenance alerts
        eventPublisher.publishEvent(new CleaningIssueReportedEvent(
                this,
                saved.getId(),
                taskId,
                task.getPropertyId(),
                task.getBookingId(),
                task.getHostId(),
                reportedBy,
                saved.getTitle(),
                saved.getSeverity()
        ));

        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CleaningIssueResponse resolveIssue(UUID issueId, String resolutionNotes, UUID resolvedBy) {
        CleaningIssue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new com.luna.aggarly.cleaning.exceptions.CleaningIssueNotFoundException(issueId));

        issue.setResolved(true);
        issue.setResolutionNotes(resolutionNotes);
        issue.setResolvedAt(Instant.now());

        CleaningIssue saved = issueRepository.save(issue);
        log.info("Cleaning issue resolved: id={}, by={}", issueId, resolvedBy);
        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningIssueResponse> getIssuesByTaskId(UUID taskId) {
        List<CleaningIssue> issues = issueRepository.findByCleaningTaskId(taskId);
        return cleaningMapper.toIssueResponseList(issues);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CleaningIssueResponse> getIssuesByProperty(UUID propertyId, Pageable pageable) {
        return issueRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId, pageable)
                .map(cleaningMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CleaningIssueResponse> getUnresolvedIssues(Pageable pageable) {
        return issueRepository.findByResolvedFalseOrderByCreatedAtDesc(pageable)
                .map(cleaningMapper::toResponse);
    }
}
