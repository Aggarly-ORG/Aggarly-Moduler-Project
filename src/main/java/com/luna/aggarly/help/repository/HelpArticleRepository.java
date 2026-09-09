package com.luna.aggarly.help.repository;

import com.luna.aggarly.help.entity.HelpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HelpArticleRepository extends JpaRepository<HelpArticle, UUID> {
    Optional<HelpArticle> findBySlug(String slug);
    List<HelpArticle> findByCategoryIgnoreCase(String category);

    @Query("SELECT a FROM HelpArticle a WHERE " +
           "(:category IS NULL OR LOWER(a.category) = LOWER(:category)) AND " +
           "(:query IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<HelpArticle> searchArticles(@Param("query") String query, @Param("category") String category);
}