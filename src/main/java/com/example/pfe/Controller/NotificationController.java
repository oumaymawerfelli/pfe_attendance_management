package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.NotificationService;
import com.example.pfe.Service.SseEmitterService;
import com.example.pfe.dto.NotificationDTO;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService   sseEmitterService;
    private final UserRepository      userRepository;

    // ── SSE stream ────────────────────────────────────────────────────────────

    /**
     * GET /api/notifications/stream
     * Browser opens this as an EventSource and keeps it alive.
     * The server pushes new notifications over this connection.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        log.info("SSE connection opened for user {}", userId);
        return sseEmitterService.createEmitter(userId);
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * GET /api/notifications?page=0&size=20
     * Returns the user's notification history, newest first.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = resolveUserId(userDetails);
        return ResponseEntity.ok(
                notificationService.getForUser(userId, PageRequest.of(page, size)));
    }

    /**
     * GET /api/notifications/unread-count
     * Returns the number of unread notifications — used for the bell badge.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId  = resolveUserId(userDetails);
        long count   = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ── Mark read ─────────────────────────────────────────────────────────────

    /** PATCH /api/notifications/{id}/read — mark a single notification read */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        notificationService.markRead(id, resolveUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/notifications/read-all — mark all notifications read */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {

        notificationService.markAllRead(resolveUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }
}