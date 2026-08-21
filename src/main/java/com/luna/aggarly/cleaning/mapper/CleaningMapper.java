package com.luna.aggarly.cleaning.mapper;

import com.luna.aggarly.cleaning.dto.request.CreateCleaningTaskRequest;
import com.luna.aggarly.cleaning.dto.response.*;
import com.luna.aggarly.cleaning.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CleaningMapper {

    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "actualStartedAt", ignore = true)
    @Mapping(target = "actualCompletedAt", ignore = true)
    @Mapping(target = "hostFeedback", ignore = true)
    @Mapping(target = "ratingByHost", ignore = true)
    @Mapping(target = "checklists", ignore = true)
    @Mapping(target = "photos", ignore = true)
    @Mapping(target = "issues", ignore = true)
    @Mapping(target = "hostId", ignore = true)
    CleaningTask toEntity(CreateCleaningTaskRequest request);

    CleaningTaskResponse toResponse(CleaningTask task);

    @Mapping(target = "checklistRoomsCount", expression = "java(task.getChecklists() != null ? task.getChecklists().size() : 0)")
    @Mapping(target = "photosCount", expression = "java(task.getPhotos() != null ? task.getPhotos().size() : 0)")
    @Mapping(target = "issuesCount", expression = "java(task.getIssues() != null ? task.getIssues().size() : 0)")
    CleaningTaskSummaryResponse toSummaryResponse(CleaningTask task);

    List<CleaningTaskSummaryResponse> toSummaryResponseList(List<CleaningTask> tasks);

    @Mapping(target = "cleaningTaskId", source = "cleaningTask.id")
    CleaningChecklistResponse toResponse(CleaningChecklist checklist);

    List<CleaningChecklistResponse> toChecklistResponseList(List<CleaningChecklist> checklists);

    @Mapping(target = "checklistId", source = "checklist.id")
    CleaningChecklistItemResponse toResponse(CleaningChecklistItem item);

    List<CleaningChecklistItemResponse> toChecklistItemResponseList(List<CleaningChecklistItem> items);

    @Mapping(target = "cleaningTaskId", source = "cleaningTask.id")
    CleaningPhotoResponse toResponse(CleaningPhoto photo);

    List<CleaningPhotoResponse> toPhotoResponseList(List<CleaningPhoto> photos);

    @Mapping(target = "cleaningTaskId", source = "cleaningTask.id")
    CleaningIssueResponse toResponse(CleaningIssue issue);

    List<CleaningIssueResponse> toIssueResponseList(List<CleaningIssue> issues);
}
