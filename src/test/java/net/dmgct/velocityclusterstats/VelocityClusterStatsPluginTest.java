package net.dmgct.velocityclusterstats;

import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VelocityClusterStatsPluginTest {
    @TempDir
    Path tempDir;

    @Test
    void pluginAnnotationContainsVelocityMetadata() {
        Plugin plugin = VelocityClusterStatsPlugin.class.getAnnotation(Plugin.class);

        assertNotNull(plugin);
        assertEquals("velocity-cluster-stats", plugin.id());
        assertEquals("Velocity Cluster Stats", plugin.name());
    }

    @Test
    void constructorAcceptsVelocityInjectedDependencies() {
        VelocityClusterStatsPlugin plugin = new VelocityClusterStatsPlugin(
                mock(ProxyServer.class),
                mock(Logger.class),
                tempDir
        );

        assertTrue(plugin instanceof VelocityClusterStatsPlugin);
    }
}
