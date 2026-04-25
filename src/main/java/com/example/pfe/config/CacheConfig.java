package com.example.pfe.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Activates Spring's annotation-driven caching.
 *
 * By default this uses a simple ConcurrentHashMap-backed cache (no extra
 * dependency needed). To switch to Redis or Caffeine later, just add the
 * dependency and a CacheManager bean — no other code changes required.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    // Spring Boot auto-configures a ConcurrentMapCacheManager when no other
    // CacheManager bean is present. That is sufficient for this use case.
}