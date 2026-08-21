package com.luna.aggarly.cleaning.service.impl;

import com.luna.aggarly.cleaning.dto.request.SubmitChecklistItemRequest;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistItemResponse;
import com.luna.aggarly.cleaning.dto.response.CleaningChecklistResponse;
import com.luna.aggarly.cleaning.entity.CleaningChecklist;
import com.luna.aggarly.cleaning.entity.CleaningChecklistItem;
import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.exceptions.CleaningChecklistNotFoundException;
import com.luna.aggarly.cleaning.exceptions.UnauthorizedCleanerAccessException;
import com.luna.aggarly.cleaning.mapper.CleaningMapper;
import com.luna.aggarly.cleaning.repository.CleaningChecklistItemRepository;
import com.luna.aggarly.cleaning.repository.CleaningChecklistRepository;
import com.luna.aggarly.cleaning.service.CleaningChecklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningChecklistServiceImpl implements CleaningChecklistService {

    private final CleaningChecklistRepository checklistRepository;
    private final CleaningChecklistItemRepository itemRepository;
    private final CleaningMapper cleaningMapper;

    @Override
    @Transactional
    public void generateDefaultChecklistsForTask(CleaningTask task, int bedrooms, int bathrooms) {
        log.info("Generating default cleaning checklists for task: {}, bedrooms: {}, bathrooms: {}",
                task.getId(), bedrooms, bathrooms);

        List<CleaningChecklist> checklists = new ArrayList<>();
        int order = 1;

        // 1. Kitchen & Dining
        CleaningChecklist kitchen = CleaningChecklist.builder()
                .cleaningTask(task)
                .roomName("Kitchen & Dining")
                .displayOrder(order++)
                .build();
        checklists.add(kitchen);

        // 2. Living Room & Common Areas
        CleaningChecklist living = CleaningChecklist.builder()
                .cleaningTask(task)
                .roomName("Living Room & Entrance")
                .displayOrder(order++)
                .build();
        checklists.add(living);

        // 3. Bedrooms
        int numBeds = Math.max(1, bedrooms);
        for (int i = 1; i <= numBeds; i++) {
            CleaningChecklist bed = CleaningChecklist.builder()
                    .cleaningTask(task)
                    .roomName(numBeds == 1 ? "Main Bedroom" : "Bedroom " + i)
                    .displayOrder(order++)
                    .build();
            checklists.add(bed);
        }

        // 4. Bathrooms
        int numBaths = Math.max(1, bathrooms);
        for (int i = 1; i <= numBaths; i++) {
            CleaningChecklist bath = CleaningChecklist.builder()
                    .cleaningTask(task)
                    .roomName(numBaths == 1 ? "Main Bathroom" : "Bathroom " + i)
                    .displayOrder(order++)
                    .build();
            checklists.add(bath);
        }

        checklistRepository.saveAll(checklists);

        // Populate items for each room checklist
        List<CleaningChecklistItem> items = new ArrayList<>();
        for (CleaningChecklist cl : checklists) {
            String name = cl.getRoomName();
            if (name.contains("Kitchen")) {
                items.add(createItem(cl, "Wipe countertops and disinfect cooking surfaces"));
                items.add(createItem(cl, "Clean inside/outside microwave, stovetop, and refrigerator"));
                items.add(createItem(cl, "Empty dishwasher, wash used dishes, restock coffee/tea/supplies"));
                items.add(createItem(cl, "Empty trash can and replace with fresh liner"));
                items.add(createItem(cl, "Sweep and mop kitchen floor"));
            } else if (name.contains("Living")) {
                items.add(createItem(cl, "Dust TV, tables, shelves, and remotes"));
                items.add(createItem(cl, "Vacuum couch, rugs, and underneath cushions"));
                items.add(createItem(cl, "Check window latches, air conditioning, and thermostat settings"));
                items.add(createItem(cl, "Mop hardwood/tile floors"));
            } else if (name.contains("Bedroom")) {
                items.add(createItem(cl, "Strip and replace bedsheets, pillowcases, and duvet covers with fresh laundry"));
                items.add(createItem(cl, "Inspect mattress and pillows for cleanliness/stains"));
                items.add(createItem(cl, "Dust nightstands, lamps, and wardrobe closet"));
                items.add(createItem(cl, "Vacuum carpet and mop floor underneath bed"));
            } else if (name.contains("Bathroom")) {
                items.add(createItem(cl, "Scrub and disinfect shower stall, bathtub, and glass doors"));
                items.add(createItem(cl, "Clean and disinfect toilet inside, outside, base, and flush handle"));
                items.add(createItem(cl, "Wipe sink vanity, polish faucets and mirror"));
                items.add(createItem(cl, "Restock fresh bath towels, hand towels, toilet paper, and toiletries"));
                items.add(createItem(cl, "Empty trash bin and mop bathroom floor"));
            }
        }

        itemRepository.saveAll(items);
        log.info("Successfully created {} checklists and {} items for cleaning task {}",
                checklists.size(), items.size(), task.getId());
    }

    private CleaningChecklistItem createItem(CleaningChecklist checklist, String description) {
        return CleaningChecklistItem.builder()
                .checklist(checklist)
                .taskDescription(description)
                .completed(false)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningChecklistResponse> getChecklistsByTaskId(UUID taskId) {
        List<CleaningChecklist> checklists = checklistRepository.findByCleaningTaskIdOrderByDisplayOrderAsc(taskId);
        return cleaningMapper.toChecklistResponseList(checklists);
    }

    @Override
    @Transactional
    public CleaningChecklistItemResponse updateChecklistItem(UUID itemId, SubmitChecklistItemRequest request, UUID currentUserId) {
        CleaningChecklistItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CleaningChecklistNotFoundException(itemId));

        CleaningTask task = item.getChecklist().getCleaningTask();
        boolean isAuthorized = task.getHostId().equals(currentUserId)
                || (task.getAssignedCleanerId() != null && task.getAssignedCleanerId().equals(currentUserId));

        if (!isAuthorized) {
            throw new UnauthorizedCleanerAccessException("You are not authorized to update this checklist item");
        }

        item.setCompleted(request.completed());
        item.setCompletedAt(request.completed() ? Instant.now() : null);
        if (request.notes() != null) {
            item.setNotes(request.notes());
        }

        CleaningChecklistItem saved = itemRepository.save(item);
        return cleaningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areAllChecklistItemsCompleted(UUID taskId) {
        List<CleaningChecklist> checklists = checklistRepository.findByCleaningTaskIdOrderByDisplayOrderAsc(taskId);
        if (checklists.isEmpty()) {
            return true;
        }
        for (CleaningChecklist cl : checklists) {
            for (CleaningChecklistItem item : cl.getItems()) {
                if (!item.isCompleted()) {
                    return false;
                }
            }
        }
        return true;
    }
}
