package com.example.pfe.Controller;

import com.example.pfe.Service.NotificationService;
import com.example.pfe.Service.SseEmitterService;
import com.example.pfe.dto.NotificationDTO;
import com.example.pfe.config.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Notification endpoints.
 *
 * Key improvement over the previous version:
 *   - Removed UserRepository dependency entirely.
 *   - resolveUserId() is gone — replaced by @AuthenticationPrincipal UserPrincipal.
 *   - UserPrincipal.getId() reads the ID from the security context (set during
 *     JWT authentication) with ZERO database calls.
 *   - This fixes the HikariCP connection leak on the SSE endpoint: the previous
 *     version held a DB connection open for the entire lifetime of every SSE
 *     connection (potentially hours), exhausting the connection pool.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService   sseEmitterService;
    // ← UserRepository removed — no longer needed here

    // ── SSE stream ────────────────────────────────────────────

    /**
     * GET /api/notifications/stream
     *
     * Browser opens this as an EventSource and keeps it alive.
     * The server pushes new notifications over this connection.
     *
     * Fix: userId comes from UserPrincipal (already in memory from JWT filter).
     * No DB connection is opened or held during the SSE lifetime.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        log.info("SSE connection opened for user {}", userId);
        return sseEmitterService.createEmitter(userId);
    }

    // ── History ───────────────────────────────────────────────

    /**
     * GET /api/notifications?page=0&size=20
     * Returns the user's notification history, newest first.
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                notificationService.getForUser(
                        principal.getId(), PageRequest.of(page, size)));
    }

    /**
     * GET /api/notifications/unread-count
     * Returns the number of unread notifications — used for the bell badge.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        long count = notificationService.getUnreadCount(principal.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ── Mark read ─────────────────────────────────────────────

    /** PATCH /api/notifications/{id}/read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markRead(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/notifications/read-all */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }
}