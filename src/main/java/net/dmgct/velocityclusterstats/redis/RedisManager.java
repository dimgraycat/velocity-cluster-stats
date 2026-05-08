package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.config.PluginConfig;
import org.slf4j.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns Redis connection pooling and tracks degraded/restored availability state.
 */
public final class RedisManager implements AutoCloseable {
    private final PluginConfig config;
    private final Logger logger;
    private final JedisPool pool;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicLong lastFailureLogMillis = new AtomicLong(0L);
    private final Clock clock;

    /**
     * Creates a Redis manager from runtime config.
     *
     * @param config runtime config
     * @param logger plugin logger
     */
    public RedisManager(PluginConfig config, Logger logger) {
        this(config, logger, Clock.systemUTC());
    }

    RedisManager(PluginConfig config, Logger logger, Clock clock) {
        this.config = config;
        this.logger = logger;
        this.clock = clock;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(4);
        poolConfig.setMaxIdle(2);
        poolConfig.setMinIdle(0);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setBlockWhenExhausted(false);

        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(config.redis().connectionTimeoutMillis())
                .socketTimeoutMillis(config.redis().socketTimeoutMillis())
                .database(config.redis().database());
        if (!config.redis().password().isBlank()) {
            clientConfig.password(config.redis().password());
        }
        this.pool = new JedisPool(
                poolConfig,
                new HostAndPort(config.redis().host(), config.redis().port()),
                clientConfig.build()
        );
    }

    /**
     * Borrows a Jedis connection from the pool.
     *
     * @return Redis connection
     */
    public Jedis getResource() {
        return pool.getResource();
    }

    /**
     * Checks Redis availability with configured timeouts.
     *
     * @return {@code true} when Redis responds
     */
    public boolean ping() {
        try (Jedis jedis = getResource()) {
            jedis.ping();
            markSuccess();
            return true;
        } catch (RuntimeException exception) {
            markFailure(exception);
            return false;
        }
    }

    /**
     * Marks Redis as available and logs a one-time restored message after failures.
     */
    public void markSuccess() {
        if (available.compareAndSet(false, true)) {
            logger.info("Redis connection restored.");
        }
    }

    /**
     * Marks Redis as unavailable and logs failures with configured cooldown.
     *
     * @param exception Redis operation failure
     */
    public void markFailure(RuntimeException exception) {
        available.set(false);
        long now = clock.millis();
        long cooldownMillis = Math.max(0L, config.redis().failureLogCooldownSeconds()) * 1000L;
        long previous = lastFailureLogMillis.get();
        if (now - previous >= cooldownMillis && lastFailureLogMillis.compareAndSet(previous, now)) {
            logger.warn("Redis connection failed: {}. Stats are temporarily unavailable.", exception.getMessage());
        }
    }

    /**
     * Returns the last known Redis availability state.
     *
     * @return {@code true} when Redis is considered available
     */
    public boolean isAvailable() {
        return available.get();
    }

    @Override
    /**
     * Closes the Redis connection pool.
     */
    public void close() {
        pool.close();
    }
}
