package net.dmgct.velocityclusterstats.command;

import net.dmgct.velocityclusterstats.config.ConfigLoader;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Loads a fresh runtime configuration and prepares a matching Redis manager for reload.
 */
public final class ReloadService {
    private final ConfigLoader configLoader;
    private final Logger logger;

    /**
     * Creates a reload service.
     *
     * @param configLoader configuration loader
     * @param logger plugin logger
     */
    public ReloadService(ConfigLoader configLoader, Logger logger) {
        this.configLoader = configLoader;
        this.logger = logger;
    }

    /**
     * Loads config and creates a new Redis manager.
     *
     * <p>Redis connection failure does not make the reload fail when the config is valid.</p>
     *
     * @return reload result containing config, Redis manager, and Redis availability
     */
    public ReloadResult load() {
        try {
            PluginConfig config = configLoader.load();
            RedisManager redisManager = new RedisManager(config, logger);
            boolean redisAvailable = redisManager.ping();
            return new ReloadResult(config, redisManager, redisAvailable, null);
        } catch (IOException | ConfigLoader.ConfigValidationException exception) {
            logger.error("Failed to reload config.", exception);
            return new ReloadResult(null, null, false, exception);
        } catch (RuntimeException exception) {
            logger.error("Unexpected reload failure.", exception);
            return new ReloadResult(null, null, false, exception);
        }
    }

    /**
     * Result of a reload attempt.
     *
     * @param config loaded config when successful
     * @param redisManager Redis manager matching the loaded config
     * @param redisAvailable whether Redis responded during reload
     * @param failure config or unexpected failure, if any
     */
    public record ReloadResult(
            PluginConfig config,
            RedisManager redisManager,
            boolean redisAvailable,
            Exception failure
    ) {
        /**
         * Returns whether config loading succeeded.
         *
         * @return {@code true} if config and Redis manager were created
         */
        public boolean success() {
            return config != null && redisManager != null && failure == null;
        }

        /**
         * Returns the user-facing reload completion message.
         *
         * @return command response message
         */
        public String message() {
            if (!success()) {
                return "[vstats] Reload failed. Check console logs.";
            }
            if (!redisAvailable) {
                return "[vstats] Reload completed, but Redis is not available.";
            }
            return "[vstats] Reload completed.";
        }
    }
}
