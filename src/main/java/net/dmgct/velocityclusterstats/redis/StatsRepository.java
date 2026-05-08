package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads active node snapshots from Redis and converts them to a cluster snapshot.
 */
public final class StatsRepository {
    /**
     * Creates a stats repository.
     */
    public StatsRepository() {
    }

    /**
     * Loads the current cluster snapshot from Redis.
     *
     * @param config current runtime config
     * @param redisManager Redis connection manager
     * @return aggregated cluster snapshot
     * @throws StatsUnavailableException when Redis cannot be read
     */
    public ClusterSnapshot loadSnapshot(PluginConfig config, RedisManager redisManager) throws StatsUnavailableException {
        RedisKeys keys = new RedisKeys(config);
        List<NodeSnapshot> snapshots = new ArrayList<>();

        try (Jedis jedis = redisManager.getResource()) {
            Set<String> nodeIds = jedis.smembers(keys.nodes());
            for (String nodeId : nodeIds) {
                Map<String, String> meta = jedis.hgetAll(keys.meta(nodeId));
                if (meta.isEmpty()) {
                    jedis.srem(keys.nodes(), nodeId);
                    continue;
                }

                String id = meta.getOrDefault("id", nodeId);
                String group = meta.getOrDefault("group", PluginConfig.GROUP_PUBLIC);
                if (!PluginConfig.GROUP_PUBLIC.equals(group) && !PluginConfig.GROUP_STAFF.equals(group)) {
                    continue;
                }

                Set<String> playerSet = jedis.smembers(keys.players(nodeId));
                List<String> players = new ArrayList<>(playerSet);
                players.sort(String.CASE_INSENSITIVE_ORDER);

                Map<String, Integer> backends = new HashMap<>();
                for (Map.Entry<String, String> entry : jedis.hgetAll(keys.backends(nodeId)).entrySet()) {
                    try {
                        backends.put(entry.getKey(), Integer.parseInt(entry.getValue()));
                    } catch (NumberFormatException ignored) {
                        backends.put(entry.getKey(), 0);
                    }
                }

                snapshots.add(new NodeSnapshot(
                        id,
                        group,
                        parseInt(meta.get("player_count"), players.size()),
                        Collections.unmodifiableList(players),
                        Collections.unmodifiableMap(backends),
                        parseLong(meta.get("updated_at"), 0L)
                ));
            }
            redisManager.markSuccess();
            return ClusterSnapshot.fromNodes(snapshots);
        } catch (RuntimeException exception) {
            redisManager.markFailure(exception);
            throw new StatsUnavailableException(exception);
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * Indicates that stats are temporarily unavailable because Redis could not be read.
     */
    public static final class StatsUnavailableException extends Exception {
        /**
         * Creates an unavailable-stats error.
         *
         * @param cause Redis operation failure
         */
        public StatsUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
