package net.dmgct.velocityclusterstats.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot for one active Velocity node as read from Redis.
 *
 * @param id node id from config
 * @param group node group, either {@code public} or {@code staff}
 * @param playerCount total players reported by the node
 * @param players player names connected through the node
 * @param backendCounts player counts grouped by backend server name
 * @param updatedAt epoch milliseconds of the node heartbeat
 */
public record NodeSnapshot(
        String id,
        String group,
        int playerCount,
        List<String> players,
        Map<String, Integer> backendCounts,
        long updatedAt
) {
}
