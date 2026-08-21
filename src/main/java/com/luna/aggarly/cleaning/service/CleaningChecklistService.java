package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.SubmitChecklistItemRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistItemResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistResponse;
import com.luna.aggarly.cleaning.entity.CleaningTask;

import java.util.List;
import java.util.UUID;

public interface CleaningChecklistService {

    void generateDefaultChecklistsForTask(CleaningTask task, int bedrooms, int bathrooms);

    List<CleaningChecklistResponse> getChecklistsByTaskId(UUID taskId);

    CleaningChecklistItemResponse updateChecklistItem(UUID itemId, SubmitChecklistItemRequest request, UUID currentUserId);

    boolean areAllChecklistItemsCompleted(UUID taskId);
}
