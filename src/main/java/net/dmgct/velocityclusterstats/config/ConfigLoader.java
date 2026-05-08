package net.dmgct.velocityclusterstats.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and validates the plugin YAML configuration from Velocity's plugin data directory.
 */
public final class ConfigLoader {
    private static final String DEFAULT_CONFIG_RESOURCE = "config.yml";

    private final Path dataDirectory;

    /**
     * Creates a loader for a Velocity plugin data directory.
     *
     * @param dataDirectory directory containing {@code config.yml}
     */
    public ConfigLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Returns the expected plugin configuration path.
     *
     * @return path to {@code config.yml}
     */
    public Path configPath() {
        return dataDirectory.resolve("config.yml");
    }

    /**
     * Creates the plugin data directory and copies the bundled default config if absent.
     *
     * @return {@code true} when a new config file was generated
     * @throws IOException if the directory or config file cannot be created
     */
    public boolean ensureDefaultConfig() throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = configPath();
        if (Files.exists(configPath)) {
            return false;
        }

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
            }
            Files.copy(inputStream, configPath);
        }
        return true;
    }

    /**
     * Loads and validates {@code config.yml}.
     *
     * @return validated runtime configuration
     * @throws IOException if the file cannot be read
     * @throws ConfigValidationException if required values are missing or invalid
     */
    public PluginConfig load() throws IOException, ConfigValidationException {
        try (InputStream inputStream = Files.newInputStream(configPath())) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new ConfigValidationException("config.yml must contain a YAML mapping");
            }
            return parse(root);
        }
    }

    private PluginConfig parse(Map<?, ?> root) throws ConfigValidationException {
        Map<?, ?> redis = section(root, "redis");
        Map<?, ?> node = section(root, "node");
        Map<?, ?> heartbeat = section(root, "heartbeat");
        Map<?, ?> backend = section(root, "backend");
        Map<?, ?> command = section(root, "command");
        Map<?, ?> permissions = section(root, "permissions");

        PluginConfig config = new PluginConfig(
                new PluginConfig.RedisConfig(
                        string(redis, "host", "127.0.0.1"),
                        integer(redis, "port", 6379, 1, 65535),
                        string(redis, "password", ""),
                        integer(redis, "database", 0, 0, 15),
                        string(redis, "key-prefix", "vstats"),
                        integer(redis, "connection-timeout-millis", 1000, 1, 60000),
                        integer(redis, "socket-timeout-millis", 1000, 1, 60000),
                        integer(redis, "failure-log-cooldown-seconds", 60, 0, 86400)
                ),
                new PluginConfig.NodeConfig(
                        string(node, "id", "prx01"),
                        string(node, "group", PluginConfig.GROUP_PUBLIC)
                ),
                new PluginConfig.HeartbeatConfig(
                        integer(heartbeat, "interval-seconds", 5, 1, 3600),
                        integer(heartbeat, "ttl-seconds", 15, 1, 86400)
                ),
                new PluginConfig.BackendConfig(
                        bool(backend, "enabled", true),
                        string(backend, "unassigned-name", "unassigned")
                ),
                new PluginConfig.CommandConfig(
                        string(command, "primary", "vstats")
                ),
                new PluginConfig.PermissionsConfig(
                        string(permissions, "view", "vstats.view"),
                        string(permissions, "staff", "vstats.staff"),
                        string(permissions, "list", "vstats.list"),
                        string(permissions, "reload", "vstats.reload")
                )
        );

        validate(config);
        return config;
    }

    private static void validate(PluginConfig config) throws ConfigValidationException {
        requireToken(config.node().id(), "node.id");
        requireGroup(config.node().group());
        requireToken(config.redis().keyPrefix(), "redis.key-prefix");
        requireNonBlank(config.backend().unassignedName(), "backend.unassigned-name");
        requireToken(config.command().primary(), "command.primary");
        requireNonBlank(config.permissions().view(), "permissions.view");
        requireNonBlank(config.permissions().staff(), "permissions.staff");
        requireNonBlank(config.permissions().list(), "permissions.list");
        requireNonBlank(config.permissions().reload(), "permissions.reload");
    }

    private static void requireGroup(String group) throws ConfigValidationException {
        if (!PluginConfig.GROUP_PUBLIC.equals(group) && !PluginConfig.GROUP_STAFF.equals(group)) {
            throw new ConfigValidationException("node.group must be public or staff");
        }
    }

    private static void requireToken(String value, String path) throws ConfigValidationException {
        requireNonBlank(value, path);
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new ConfigValidationException(path + " must contain only letters, numbers, hyphen, or underscore");
        }
    }

    private static void requireNonBlank(String value, String path) throws ConfigValidationException {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(path + " must not be blank");
        }
    }

    private static Map<?, ?> section(Map<?, ?> root, String name) throws ConfigValidationException {
        Object value = root.get(name);
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigValidationException("Missing section: " + name);
        }
        return map;
    }

    private static String string(Map<?, ?> section, String key, String fallback) {
        Object value = section.get(key);
        return value == null ? fallback : Objects.toString(value);
    }

    private static boolean bool(Map<?, ?> section, String key, boolean fallback) throws ConfigValidationException {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string)) {
                return false;
            }
        }
        throw new ConfigValidationException(key + " must be a boolean");
    }

    private static int integer(Map<?, ?> section, String key, int fallback, int min, int max)
            throws ConfigValidationException {
        Object value = section.get(key);
        int parsed;
        if (value == null) {
            parsed = fallback;
        } else if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(Objects.toString(value));
            } catch (NumberFormatException exception) {
                throw new ConfigValidationException(key + " must be an integer", exception);
            }
        }
        if (parsed < min || parsed > max) {
            throw new ConfigValidationException(key + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    /**
     * Indicates that the YAML file was readable but does not satisfy plugin requirements.
     */
    public static final class ConfigValidationException extends Exception {
        /**
         * Creates a validation error with a human-readable message.
         *
         * @param message validation failure description
         */
        public ConfigValidationException(String message) {
            super(message);
        }

        /**
         * Creates a validation error with its underlying cause.
         *
         * @param message validation failure description
         * @param cause underlying parse or conversion failure
         */
        public ConfigValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
