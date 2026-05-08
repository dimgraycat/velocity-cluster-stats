package net.dmgct.velocityclusterstats;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Plugin(
        id = "velocity-cluster-stats",
        name = "Velocity Cluster Stats",
        version = "0.1.0-SNAPSHOT",
        description = "Aggregates player counts across Velocity proxy nodes.",
        authors = {"dmgct"}
)
public final class VelocityClusterStatsPlugin {
    private static final String DEFAULT_CONFIG_RESOURCE = "config.yml";

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public VelocityClusterStatsPlugin(
            ProxyServer proxyServer,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ensureDefaultConfig();
        logger.info("Velocity Cluster Stats initialized for Velocity {}.", proxyServer.getVersion().getVersion());
    }

    private void ensureDefaultConfig() {
        Path configPath = dataDirectory.resolve("config.yml");

        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(configPath)) {
                return;
            }

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
                }
                Files.copy(inputStream, configPath, StandardCopyOption.COPY_ATTRIBUTES);
            }

            logger.info("config.yml was not found. Generated default config: {}", configPath);
        } catch (IOException | RuntimeException exception) {
            logger.error("Failed to generate default config at {}. Plugin will remain loaded.", configPath, exception);
        }
    }
}
