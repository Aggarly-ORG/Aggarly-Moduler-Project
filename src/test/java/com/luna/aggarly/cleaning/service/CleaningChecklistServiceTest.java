package com.luna.aggarly.cleaning.service;

import com.luna.aggarly.cleaning.dto.request.SubmitChecklistItemRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistItemResponse;
import com.luna.aggarly.cleaning.entity.CleaningChecklist;
import com.luna.aggarly.cleaning.entity.CleaningChecklistItem;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningChecklistItemRepository;
import com.luna.aggarly.cleaning.repository.CleaningChecklistRepository;
import com.luna.aggarly.cleaning.repository.CleaningTaskRepository;
import com.luna.aggarly.cleaning.service.impl.CleaningChecklistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleaningChecklistServiceTest {

    @Mock
    private CleaningChecklistRepository checklistRepository;

    @Mock
    private CleaningChecklistItemRepository itemRepository;

    @Mock
    private CleaningTaskRepository taskRepository;

    @Mock
    private CleaningMapper cleaningMapper;

    @InjectMocks
    private CleaningChecklistServiceImpl checklistService;

    private UUID taskId;
    private UUID checklistId;
    private UUID itemId;
    private UUID cleanerId;
    private UUID hostId;
    private CleaningTask sampleTask;
    private CleaningChecklist sampleChecklist;
    private CleaningChecklistItem sampleItem;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        checklistId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        cleanerId = UUID.randomUUID();
        hostId = UUID.randomUUID();

        sampleTask = CleaningTask.builder()
                .hostId(hostId)
                .assignedCleanerId(cleanerId)
                .status(CleaningStatus.IN_PROGRESS)
                .checklists(new ArrayList<>())
                .build();
        sampleTask.setId(taskId);

        sampleChecklist = CleaningChecklist.builder()
                .cleaningTask(sampleTask)
                .roomName("Living Room")
                .displayOrder(1)
                .items(new ArrayList<>())
                .build();
        sampleChecklist.setId(checklistId);

        sampleItem = CleaningChecklistItem.builder()
                .checklist(sampleChecklist)
                .taskDescription("Vacuum carpet")
                .completed(false)
                .build();
        sampleItem.setId(itemId);
        sampleChecklist.getItems().add(sampleItem);
        sampleTask.getChecklists().add(sampleChecklist);
    }

    @Test
    @DisplayName("generateDefaultChecklistsForTask should create checklist rooms and items according to specs")
    void testGenerateDefaultChecklists() {
        checklistService.generateDefaultChecklistsForTask(sampleTask, 2, 1);

        // Standard living room, kitchen, 2 bedrooms, 1 bathroom
        verify(checklistRepository).saveAll(any());
        verify(itemRepository).saveAll(any());
    }

    @Test
    @DisplayName("updateChecklistItem should update completed status and record completedAt")
    void testUpdateChecklistItem() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(sampleItem));
        when(itemRepository.save(any(CleaningChecklistItem.class))).thenReturn(sampleItem);
        when(cleaningMapper.toResponse(sampleItem)).thenReturn(new CleaningChecklistItemResponse(
                itemId, checklistId, "Vacuum carpet", true, Instant.now(), null
        ));

        SubmitChecklistItemRequest request = new SubmitChecklistItemRequest(true, null);
        CleaningChecklistItemResponse response = checklistService.updateChecklistItem(itemId, request, cleanerId);

        assertThat(response.completed()).isTrue();
        assertThat(sampleItem.isCompleted()).isTrue();
        assertThat(sampleItem.getCompletedAt()).isNotNull();
    }
}
