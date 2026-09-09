package com.luna.aggarly.help.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "help_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpArticle extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}