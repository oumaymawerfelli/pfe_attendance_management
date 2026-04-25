package com.example.pfe.Service;

import com.example.pfe.dto.NotificationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one active SSE connection per user.
 * When NotificationService creates a notification it calls
 * {@link #push(Long, NotificationDTO)} to deliver it instantly.
 */
@Service
@Slf4j
public class SseEmitterService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long userId) {
        SseEmitter old = emitters.remove(userId);
        if (old != null) { try { old.complete(); } catch (Exception ignored) {} }

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(()     -> emitters.remove(userId));
        emitter.onError(e        -> emitters.remove(userId));

        emitters.put(userId, emitter);

        log.debug("SSE emitter created for user {}", userId);
        return emitter;
    }

    public void push(Long userId, NotificationDTO dto) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(dto, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to push SSE to user {} — removing emitter", userId);
            emitters.remove(userId);
        }
    }
}