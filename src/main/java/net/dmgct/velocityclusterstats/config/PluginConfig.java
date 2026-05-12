package net.dmgct.velocityclusterstats.config;

/**
 * Immutable runtime configuration loaded from {@code plugins/velocity-cluster-stats/config.yml}.
 *
 * @param redis Redis connection and timeout settings
 * @param node local Velocity node identity
 * @param heartbeat heartbeat interval and Redis TTL settings
 * @param backend backend server snapshot settings
 * @param command command registration settings
 * @param permissions Velocity permission nodes used by commands
 */
public record PluginConfig(
        RedisConfig redis,
        NodeConfig node,
        HeartbeatConfig heartbeat,
        BackendConfig backend,
        CommandConfig command,
        PermissionsConfig permissions
) {
    public static final String GROUP_PUBLIC = "public";
    public static final String GROUP_STAFF = "staff";

    /**
     * Redis connection settings used by the heartbeat publisher and stats repository.
     *
     * @param host Redis host
     * @param port Redis port
     * @param password Redis password, or blank when no password is used
     * @param database Redis database index
     * @param keyPrefix Redis key prefix for this plugin
     * @param connectionTimeoutMillis Redis connection timeout
     * @param socketTimeoutMillis Redis socket read/write timeout
     * @param failureLogCooldownSeconds cooldown for repeated Redis failure logs
     */
    public record RedisConfig(
            String host,
            int port,
            String password,
            int database,
            String keyPrefix,
            int connectionTimeoutMillis,
            int socketTimeoutMillis,
            int failureLogCooldownSeconds
    ) {
    }

    /**
     * Local Velocity node identity. The {@code id} is also used for Redis keys and display output.
     *
     * @param id unique Velocity node id
     * @param group node group, either {@code public} or {@code staff}
     */
    public record NodeConfig(String id, String group) {
    }

    /**
     * Controls how often this node publishes snapshots and how long Redis keys stay active.
     *
     * @param intervalSeconds heartbeat publish interval
     * @param ttlSeconds TTL applied to per-node Redis keys
     */
    public record HeartbeatConfig(int intervalSeconds, int ttlSeconds) {
    }

    /**
     * Backend server reporting settings.
     *
     * @param enabled whether backend server names are collected
     * @param unassignedName backend name used when a player is not connected to a backend
     */
    public record BackendConfig(boolean enabled, String unassignedName) {
    }

    /**
     * Command registration settings.
     *
     * @param primary primary command alias
     * @param snapshotCacheMillis milliseconds to reuse a recently loaded Redis snapshot for commands
     * @param playerListLimit maximum player names shown by one list command response
     */
    public record CommandConfig(String primary, int snapshotCacheMillis, int playerListLimit) {
    }

    /**
     * Permission node names delegated to Velocity's permission system.
     *
     * @param view permission for summary/public/server views
     * @param staff permission for staff-only stats
     * @param list permission for player list commands
     * @param reload permission for config reload
     */
    public record PermissionsConfig(String view, String staff, String list, String reload) {
    }
}
