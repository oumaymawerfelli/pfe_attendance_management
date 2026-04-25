package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceConfigRepository;
import com.example.pfe.dto.AttendanceConfigDTO;
import com.example.pfe.entities.AttendanceConfig;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceConfigService {

    private static final String CACHE_NAME = "attendanceConfig";

    private final AttendanceConfigRepository configRepository;

    // ── Read all (HR settings page) ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceConfigDTO.Response> getAllConfigs() {
        return configRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Cached single-value read (used by AttendanceService on every login) ────

    /**
     * Returns the numeric value for the given key.
     * Result is cached under "attendanceConfig" so the DB is queried only once
     * per key until HR changes the value.
     *
     * @throws ResourceNotFoundException if the key is missing (seeding not run?)
     */
    @Cacheable(value = CACHE_NAME, key = "#key")
    @Transactional(readOnly = true)
    public double getValue(String key) {
        return configRepository.findByConfigKey(key)
                .map(AttendanceConfig::getConfigValue)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attendance config key not found: " + key));
    }

    // Convenience typed getters used by AttendanceService ──────────────────────

    public int    getInt   (String key) { return (int) getValue(key); }
    public double getDouble(String key) { return getValue(key); }

    // ── Update (HR action) ─────────────────────────────────────────────────────

    /**
     * Updates the value for an existing config key and evicts the cache entry
     * so the next check-in picks up the new threshold immediately.
     */
    @CacheEvict(value = CACHE_NAME, key = "#key")
    @Transactional
    public AttendanceConfigDTO.Response updateConfig(String key,
                                                     AttendanceConfigDTO.UpdateRequest request) {
        AttendanceConfig config = configRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attendance config key not found: " + key));

        log.info("HR updating config [{}]: {} → {}  (by {})",
                key, config.getConfigValue(), request.getConfigValue(),
                request.getLastModifiedBy());

        config.setConfigValue(request.getConfigValue());
        if (request.getLastModifiedBy() != null) {
            config.setLastModifiedBy(request.getLastModifiedBy());
        }

        return toResponse(configRepository.save(config));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private AttendanceConfigDTO.Response toResponse(AttendanceConfig c) {
        return AttendanceConfigDTO.Response.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(c.getConfigValue())
                .description(c.getDescription())
                .lastModifiedBy(c.getLastModifiedBy())
                .lastModifiedAt(c.getLastModifiedAt())
                .build();
    }
}