package net.dmgct.velocityclusterstats;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.dmgct.velocityclusterstats.command.VStatsCommand;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void applyReloadClosesOldRedisManagerAfterInFlightCommandLoadCompletes() throws Exception {
        ProxyServer proxyServer = proxyServerForReload();
        VelocityClusterStatsPlugin plugin = new VelocityClusterStatsPlugin(
                proxyServer,
                mock(Logger.class),
                tempDir
        );
        RedisManager oldManager = mock(RedisManager.class);
        RedisManager newManager = mock(RedisManager.class);
        CompletableFuture<?> previousCommandLoad = new CompletableFuture<>();
        VStatsCommand command = mock(VStatsCommand.class);
        doReturn(previousCommandLoad).when(command).clearSnapshotCache();

        referenceField(plugin, "currentConfig", PluginConfig.class).set(TestConfigs.config());
        referenceField(plugin, "redisManager", RedisManager.class).set(oldManager);
        setField(plugin, "command", command);

        plugin.applyReload(TestConfigs.config(), newManager);

        verify(oldManager, never()).close();
        previousCommandLoad.complete(null);
        verify(oldManager).close();
        verify(newManager, never()).close();
    }

    private ProxyServer proxyServerForReload() {
        ProxyServer proxyServer = mock(ProxyServer.class);

        CommandManager commandManager = mock(CommandManager.class);
        CommandMeta.Builder metaBuilder = mock(CommandMeta.Builder.class);
        when(proxyServer.getCommandManager()).thenReturn(commandManager);
        when(commandManager.metaBuilder("vstats")).thenReturn(metaBuilder);
        when(metaBuilder.plugin(any())).thenReturn(metaBuilder);
        when(metaBuilder.build()).thenReturn(mock(CommandMeta.class));

        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.TaskBuilder taskBuilder = mock(Scheduler.TaskBuilder.class);
        when(proxyServer.getScheduler()).thenReturn(scheduler);
        when(scheduler.buildTask(any(), any(Runnable.class))).thenReturn(taskBuilder);
        when(taskBuilder.delay(1, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(taskBuilder);
        when(taskBuilder.repeat(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(taskBuilder);
        when(taskBuilder.schedule()).thenReturn(mock(ScheduledTask.class));

        return proxyServer;
    }

    @SuppressWarnings("unchecked")
    private <T> AtomicReference<T> referenceField(Object target, String fieldName, Class<T> ignored) throws Exception {
        return (AtomicReference<T>) field(target, fieldName).get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        field(target, fieldName).set(target, value);
    }

    private Field field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
