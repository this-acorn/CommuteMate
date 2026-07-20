package project.group1.commutemate.Config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on {@code @Cacheable} so responses from third-party APIs (e.g. the
 * weather card) can be cached for a short time instead of being fetched on
 * every dashboard request. Cache names and expiry live in application.properties.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
