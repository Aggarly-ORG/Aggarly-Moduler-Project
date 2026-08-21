package com.luna.aggarly.cleaning.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cleaning_checklists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE cleaning_checklists SET is_deleted = true WHERE id = ?")
public class CleaningChecklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_task_id", nullable = false)
    private CleaningTask cleaningTask;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CleaningChecklistItem> items = new ArrayList<>();
}
