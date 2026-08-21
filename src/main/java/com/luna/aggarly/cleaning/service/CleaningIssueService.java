package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.ReportCleaningIssueRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningIssueResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CleaningIssueService {

    CleaningIssueResponse reportIssue(UUID taskId, ReportCleaningIssueRequest request, UUID reportedBy);

    CleaningIssueResponse resolveIssue(UUID issueId, String resolutionNotes, UUID resolvedBy);

    List<CleaningIssueResponse> getIssuesByTaskId(UUID taskId);

    Page<CleaningIssueResponse> getIssuesByProperty(UUID propertyId, Pageable pageable);

    Page<CleaningIssueResponse> getUnresolvedIssues(Pageable pageable);
}
