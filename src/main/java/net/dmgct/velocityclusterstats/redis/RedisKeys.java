package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.config.PluginConfig;

/**
 * Builds Redis keys using the configured key prefix.
 */
public final class RedisKeys {
    private final String prefix;

    /**
     * Creates a key helper for a config.
     *
     * @param config runtime config containing the key prefix
     */
    public RedisKeys(PluginConfig config) {
        this.prefix = config.redis().keyPrefix();
    }

    /**
     * Returns the active node index key.
     *
     * @return Redis set key for node ids
     */
    public String nodes() {
        return prefix + ":nodes";
    }

    /**
     * Returns the node metadata hash key.
     *
     * @param nodeId node id
     * @return Redis hash key
     */
    public String meta(String nodeId) {
        return prefix + ":nodes:" + nodeId + ":meta";
    }

    /**
     * Returns the node player-name set key.
     *
     * @param nodeId node id
     * @return Redis set key
     */
    public String players(String nodeId) {
        return prefix + ":nodes:" + nodeId + ":players";
    }

    /**
     * Returns the node player-to-backend hash key.
     *
     * @param nodeId node id
     * @return Redis hash key
     */
    public String playerServers(String nodeId) {
        return prefix + ":nodes:" + nodeId + ":player_servers";
    }

    /**
     * Returns the node backend-count hash key.
     *
     * @param nodeId node id
     * @return Redis hash key
     */
    public String backends(String nodeId) {
        return prefix + ":nodes:" + nodeId + ":backends";
    }
}
