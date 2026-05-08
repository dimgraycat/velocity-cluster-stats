package net.dmgct.velocityclusterstats.redis;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes this Velocity node's current player and backend snapshot to Redis.
 */
public final class HeartbeatPublisher {
    private final ProxyServer proxyServer;

    /**
     * Creates a heartbeat publisher.
     *
     * @param proxyServer Velocity proxy server
     */
    public HeartbeatPublisher(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * Writes one heartbeat snapshot.
     *
     * <p>Redis failures are swallowed after marking the manager unavailable so Velocity's
     * login, chat, and backend transfer flows are not blocked by stats failures.</p>
     *
     * @param config current runtime config
     * @param redisManager Redis connection manager
     */
    public void publish(PluginConfig config, RedisManager redisManager) {
        RedisKeys keys = new RedisKeys(config);
        String nodeId = config.node().id();
        int ttlSeconds = config.heartbeat().ttlSeconds();

        List<String> playerNames = new ArrayList<>();
        Map<String, String> playerServers = new HashMap<>();
        Map<String, Integer> backendCounts = new HashMap<>();

        for (Player player : proxyServer.getAllPlayers()) {
            String playerName = player.getUsername();
            String backendName = config.backend().enabled()
                    ? player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse(config.backend().unassignedName())
                    : config.backend().unassignedName();

            playerNames.add(playerName);
            playerServers.put(playerName, backendName);
            backendCounts.put(backendName, backendCounts.getOrDefault(backendName, 0) + 1);
        }
        playerNames.sort(String.CASE_INSENSITIVE_ORDER);

        Map<String, String> meta = Map.of(
                "id", nodeId,
                "group", config.node().group(),
                "player_count", String.valueOf(playerNames.size()),
                "updated_at", String.valueOf(System.currentTimeMillis())
        );
        Map<String, String> backendCountStrings = new HashMap<>();
        backendCounts.forEach((backend, count) -> backendCountStrings.put(backend, String.valueOf(count)));

        try (Jedis jedis = redisManager.getResource()) {
            jedis.sadd(keys.nodes(), nodeId);
            jedis.hset(keys.meta(nodeId), meta);

            jedis.del(keys.players(nodeId));
            if (!playerNames.isEmpty()) {
                jedis.sadd(keys.players(nodeId), playerNames.toArray(String[]::new));
            }

            jedis.del(keys.playerServers(nodeId));
            if (!playerServers.isEmpty()) {
                jedis.hset(keys.playerServers(nodeId), playerServers);
            }

            jedis.del(keys.backends(nodeId));
            if (!backendCountStrings.isEmpty()) {
                jedis.hset(keys.backends(nodeId), backendCountStrings);
            }

            jedis.expire(keys.meta(nodeId), ttlSeconds);
            jedis.expire(keys.players(nodeId), ttlSeconds);
            jedis.expire(keys.playerServers(nodeId), ttlSeconds);
            jedis.expire(keys.backends(nodeId), ttlSeconds);
            redisManager.markSuccess();
        } catch (RuntimeException exception) {
            redisManager.markFailure(exception);
        }
    }
}
