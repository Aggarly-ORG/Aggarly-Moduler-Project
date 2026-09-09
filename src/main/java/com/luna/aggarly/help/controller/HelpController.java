package com.luna.aggarly.help.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.help.dto.CreateHelpSignalRequest;
import com.luna.aggarly.help.dto.HelpArticleResponse;
import com.luna.aggarly.help.dto.HelpSignalResponse;
import com.luna.aggarly.help.entity.HelpArticle;
import com.luna.aggarly.help.entity.HelpSignal;
import com.luna.aggarly.help.repository.HelpArticleRepository;
import com.luna.aggarly.help.repository.HelpSignalRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/help")
@RequiredArgsConstructor
@Tag(name = "Nocturnal Support & Help Desk", description = "Dark-Sky Knowledge Base, Articles & Emergency Nocturnal Signals")
public class HelpController {

    private final HelpArticleRepository articleRepository;
    private final HelpSignalRepository signalRepository;

    @GetMapping("/articles")
    @Operation(summary = "Search knowledge base articles and Bortle covenants (Public)")
    public ResponseEntity<ApiResponse<List<HelpArticleResponse>>> getArticles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        List<HelpArticle> articles = articleRepository.searchArticles(
                (q != null && !q.isBlank()) ? q.trim() : null,
                (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) ? category.trim() : null
        );

        List<HelpArticleResponse> response = articles.stream().map(a -> new HelpArticleResponse(
                a.getId(),
                a.getSlug(),
                a.getTitle(),
                a.getCategory(),
                a.getContent(),
                a.getCreatedAt()
        )).toList();

        return ApiResponse.ok(response, "Articles retrieved successfully").toResponseEntity();
    }

    @PostMapping("/signals")
    @Operation(summary = "Transmit emergency nocturnal signal (locked out, smart lock failure, recalibration)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<HelpSignalResponse>> transmitSignal(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateHelpSignalRequest req) {
        UUID userId = principal != null ? principal.getUserId() : null;
        log.warn("Emergency nocturnal signal transmitted: userId={}, situation={}, ref={}",
                userId, req.situationType(), req.residencyRef());

        HelpSignal signal = HelpSignal.builder()
                .userId(userId)
                .residencyRef(req.residencyRef())
                .situationType(req.situationType())
                .description(req.description())
                .phone(req.phone())
                .status("DISPATCHED")
                .build();

        signal = signalRepository.save(signal);

        HelpSignalResponse resp = new HelpSignalResponse(
                signal.getId(),
                signal.getResidencyRef(),
                signal.getSituationType(),
                signal.getDescription(),
                signal.getPhone(),
                signal.getStatus(),
                signal.getCreatedAt(),
                signal.getResolvedAt()
        );

        return ApiResponse.created(resp, "Nocturnal signal dispatched to lead curator on duty").toResponseEntity();
    }

    @GetMapping("/tickets/me")
    @Operation(summary = "Get guest submitted nocturnal support tickets and signals", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<HelpSignalResponse>>> getMyTickets(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        List<HelpSignal> signals = signalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<HelpSignalResponse> list = signals.stream().map(s -> new HelpSignalResponse(
                s.getId(),
                s.getResidencyRef(),
                s.getSituationType(),
                s.getDescription(),
                s.getPhone(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getResolvedAt()
        )).toList();

        return ApiResponse.ok(list, "Support tickets retrieved successfully").toResponseEntity();
    }
}