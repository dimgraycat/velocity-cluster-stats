package net.dmgct.velocityclusterstats;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.dmgct.velocityclusterstats.command.MessageFormatter;
import net.dmgct.velocityclusterstats.command.ReloadService;
import net.dmgct.velocityclusterstats.command.VStatsCommand;
import net.dmgct.velocityclusterstats.config.ConfigLoader;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.redis.HeartbeatPublisher;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import net.dmgct.velocityclusterstats.redis.StatsRepository;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Velocity entry point for cluster stats.
 *
 * <p>The plugin registers proxy-side commands only, publishes heartbeat snapshots to Redis,
 * and keeps Velocity usable when Redis is unavailable.</p>
 */
@Plugin(
        id = "velocity-cluster-stats",
        name = "Velocity Cluster Stats",
        version = BuildConstants.VERSION,
        description = "Aggregates player counts across Velocity proxy nodes.",
        authors = {"dmgct"}
)
public final class VelocityClusterStatsPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ConfigLoader configLoader;
    private final AtomicReference<PluginConfig> currentConfig = new AtomicReference<>();
    private final AtomicReference<RedisManager> redisManager = new AtomicReference<>();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final HeartbeatPublisher heartbeatPublisher;
    private final StatsRepository statsRepository = new StatsRepository();
    private final MessageFormatter messageFormatter = new MessageFormatter();

    private ScheduledTask heartbeatTask;
    private CommandMeta commandMeta;
    private VStatsCommand command;

    /**
     * Creates the plugin with Velocity-injected dependencies.
     *
     * @param proxyServer Velocity proxy server
     * @param logger plugin logger
     * @param dataDirectory plugin data directory
     */
    @Inject
    public VelocityClusterStatsPlugin(
            ProxyServer proxyServer,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configLoader = new ConfigLoader(dataDirectory);
        this.heartbeatPublisher = new HeartbeatPublisher(proxyServer);
    }

    /**
     * Initializes config, Redis, heartbeat, and command registration.
     *
     * @param event Velocity initialization event
     */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            if (configLoader.ensureDefaultConfig()) {
                logger.info("config.yml was not found. Generated default config: {}", configLoader.configPath());
            }
        } catch (IOException exception) {
            logger.error("Failed to generate default config at {}. Plugin will remain loaded.", configLoader.configPath(), exception);
        }

        PluginConfig config;
        try {
            config = configLoader.load();
        } catch (IOException | ConfigLoader.ConfigValidationException exception) {
            logger.error("Failed to load config. Velocity Cluster Stats will stay loaded but stats may be unavailable.", exception);
            return;
        }

        RedisManager manager = new RedisManager(config, logger);
        currentConfig.set(config);
        redisManager.set(manager);

        ReloadService reloadService = new ReloadService(configLoader, logger);
        command = new VStatsCommand(
                this,
                proxyServer,
                currentConfig,
                redisManager,
                statsRepository,
                reloadService,
                messageFormatter,
                reloadInProgress
        );

        registerCommand(config);
        scheduleHeartbeat(config);
        proxyServer.getScheduler().buildTask(this, manager::ping).schedule();
        logger.info("Velocity Cluster Stats initialized for Velocity {}.", proxyServer.getVersion().getVersion());
    }

    /**
     * Cancels scheduled tasks and closes Redis resources on proxy shutdown.
     *
     * @param event Velocity shutdown event
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        RedisManager manager = redisManager.getAndSet(null);
        if (manager != null) {
            manager.close();
        }
    }

    /**
     * Atomically applies a successful reload result.
     *
     * @param config newly loaded config
     * @param newRedisManager Redis manager created for the new config
     */
    public void applyReload(PluginConfig config, RedisManager newRedisManager) {
        RedisManager oldRedisManager = redisManager.getAndSet(newRedisManager);
        currentConfig.set(config);
        registerCommand(config);
        scheduleHeartbeat(config);
        if (oldRedisManager != null) {
            oldRedisManager.close();
        }
    }

    private void registerCommand(PluginConfig config) {
        if (commandMeta != null) {
            proxyServer.getCommandManager().unregister(commandMeta);
        }

        String primary = config.command().primary();
        CommandMeta.Builder builder = proxyServer.getCommandManager()
                .metaBuilder(primary)
                .plugin(this);
        if (!"vstats".equals(primary)) {
            builder.aliases("vstats");
        }
        commandMeta = builder.build();
        proxyServer.getCommandManager().register(commandMeta, command);
    }

    private void scheduleHeartbeat(PluginConfig config) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        heartbeatTask = proxyServer.getScheduler()
                .buildTask(this, () -> {
                    RedisManager manager = redisManager.get();
                    PluginConfig activeConfig = currentConfig.get();
                    if (manager != null && activeConfig != null) {
                        heartbeatPublisher.publish(activeConfig, manager);
                    }
                })
                .delay(1, TimeUnit.SECONDS)
                .repeat(config.heartbeat().intervalSeconds(), TimeUnit.SECONDS)
                .schedule();
    }
}
