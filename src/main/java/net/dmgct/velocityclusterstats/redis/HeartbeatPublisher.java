package net.dmgct.velocityclusterstats.redis;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        if (config.backend().enabled()) {
            for (RegisteredServer server : proxyServer.getAllServers()) {
                backendCounts.put(server.getServerInfo().getName(), 0);
            }
        }

        for (Player player : proxyServer.getAllPlayers()) {
            String playerName = player.getUsername();

            playerNames.add(playerName);
            if (config.backend().enabled()) {
                String backendName = player.getCurrentServer()
                        .map(serverConnection -> serverConnection.getServerInfo().getName())
                        .orElse(config.backend().unassignedName());
                playerServers.put(playerName, backendName);
                backendCounts.put(backendName, backendCounts.getOrDefault(backendName, 0) + 1);
            }
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
            Transaction transaction = jedis.multi();
            transaction.sadd(keys.nodes(), nodeId);
            transaction.hset(keys.meta(nodeId), meta);
            transaction.del(keys.players(nodeId));
            if (!playerNames.isEmpty()) {
                transaction.sadd(keys.players(nodeId), playerNames.toArray(String[]::new));
            }
            transaction.del(keys.playerServers(nodeId));
            if (!playerServers.isEmpty()) {
                transaction.hset(keys.playerServers(nodeId), playerServers);
            }
            transaction.del(keys.backends(nodeId));
            if (!backendCountStrings.isEmpty()) {
                transaction.hset(keys.backends(nodeId), backendCountStrings);
            }
            transaction.expire(keys.meta(nodeId), ttlSeconds);
            transaction.expire(keys.players(nodeId), ttlSeconds);
            transaction.expire(keys.playerServers(nodeId), ttlSeconds);
            transaction.expire(keys.backends(nodeId), ttlSeconds);
            transaction.expire(keys.nodes(), ttlSeconds);
            transaction.exec();
            removeStaleNodes(jedis, keys, nodeId);
            redisManager.markSuccess();
        } catch (RuntimeException exception) {
            redisManager.markFailure(exception);
        }
    }

    /**
     * Removes this node's Redis state during a graceful Velocity shutdown.
     *
     * @param config current runtime config
     * @param redisManager Redis connection manager
     */
    public void removeNode(PluginConfig config, RedisManager redisManager) {
        RedisKeys keys = new RedisKeys(config);
        String nodeId = config.node().id();
        try (Jedis jedis = redisManager.getResource()) {
            deleteNodeKeys(jedis, keys, nodeId);
            jedis.srem(keys.nodes(), nodeId);
            removeStaleNodes(jedis, keys, nodeId);
            redisManager.markSuccess();
        } catch (RuntimeException exception) {
            redisManager.markFailure(exception);
        }
    }

    private static void removeStaleNodes(Jedis jedis, RedisKeys keys, String currentNodeId) {
        Set<String> nodeIds = jedis.smembers(keys.nodes());
        if (nodeIds == null) {
            return;
        }
        for (String nodeId : nodeIds) {
            if (!currentNodeId.equals(nodeId) && !jedis.exists(keys.meta(nodeId))) {
                deleteNodeKeys(jedis, keys, nodeId);
                jedis.srem(keys.nodes(), nodeId);
            }
        }
    }

    private static void deleteNodeKeys(Jedis jedis, RedisKeys keys, String nodeId) {
        jedis.del(keys.meta(nodeId), keys.players(nodeId), keys.playerServers(nodeId), keys.backends(nodeId));
    }
}
