package net.dmgct.velocityclusterstats.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.dmgct.velocityclusterstats.TestConfigs;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import net.dmgct.velocityclusterstats.redis.StatsRepository;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VStatsCommandTest {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @Test
    void executeRejectsUnknownSubcommandsWithoutScheduler() {
        VStatsCommand command = command(false);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "nodes");

        command.execute(invocation);

        verifyPlainMessage(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
    }

    @Test
    void executeHelpListsAllCommandsForConsoleWithoutScheduler() {
        VStatsCommand command = command(false);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "help");

        command.execute(invocation);

        verifyPlainMessage(source, """
                > /vstats
                > /vstats public
                > /vstats staff
                > /vstats servers
                > /vstats list
                > /vstats list public
                > /vstats list <nodeId>
                > /vstats list staff
                > /vstats reload
                > /vstats help""");
    }

    @Test
    void executeHelpListsOnlyPermittedCommandsForPlayer() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.getPermissionValue("vstats.view")).thenReturn(Tristate.TRUE);
        when(source.getPermissionValue("vstats.list")).thenReturn(Tristate.FALSE);
        when(source.getPermissionValue("vstats.staff")).thenReturn(Tristate.FALSE);
        when(source.getPermissionValue("vstats.reload")).thenReturn(Tristate.FALSE);
        SimpleCommand.Invocation invocation = invocation(source, "help");

        command.execute(invocation);

        verifyPlainMessage(source, """
                > /vstats
                > /vstats public
                > /vstats servers
                > /vstats help""");
    }

    @Test
    void executeHelpTreatsUndefinedPermissionsAsAllowedForPlayer() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.getPermissionValue("vstats.view")).thenReturn(Tristate.UNDEFINED);
        when(source.getPermissionValue("vstats.list")).thenReturn(Tristate.UNDEFINED);
        when(source.getPermissionValue("vstats.staff")).thenReturn(Tristate.UNDEFINED);
        when(source.getPermissionValue("vstats.reload")).thenReturn(Tristate.UNDEFINED);
        SimpleCommand.Invocation invocation = invocation(source, "help");

        command.execute(invocation);

        verifyPlainMessage(source, """
                > /vstats
                > /vstats public
                > /vstats staff
                > /vstats servers
                > /vstats list
                > /vstats list public
                > /vstats list <nodeId>
                > /vstats list staff
                > /vstats reload
                > /vstats help""");
    }

    @Test
    void executeRejectsListWithTooManyArguments() {
        VStatsCommand command = command(false);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "list", "prx01", "extra");

        command.execute(invocation);

        verifyPlainMessage(source, "Usage: /vstats list [nodeId|public|staff]");
    }

    @Test
    void executeListPublicReturnsOnlyPublicPlayers() throws Exception {
        ProxyServer proxyServer = proxyServerThatRunsTasksImmediately();
        StatsRepository repository = mock(StatsRepository.class);
        RedisManager redisManager = mock(RedisManager.class);
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 2, List.of("Beta", "alpha"), Map.of(), 1L),
                new NodeSnapshot("staff", PluginConfig.GROUP_STAFF, 1, List.of("StaffA"), Map.of(), 1L)
        ));
        when(repository.loadSnapshot(TestConfigs.config(), redisManager)).thenReturn(snapshot);

        VStatsCommand command = new VStatsCommand(
                null,
                proxyServer,
                new AtomicReference<>(TestConfigs.config()),
                new AtomicReference<>(redisManager),
                repository,
                null,
                new MessageFormatter(),
                new AtomicBoolean(false)
        );
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source, "list", "public"));

        verifyPlainMessage(source, "There are 2 player(s): alpha, Beta");
    }

    @Test
    void executeListAppliesConfiguredDisplayLimit() throws Exception {
        ProxyServer proxyServer = proxyServerThatRunsTasksImmediately();
        StatsRepository repository = mock(StatsRepository.class);
        RedisManager redisManager = mock(RedisManager.class);
        PluginConfig config = new PluginConfig(
                TestConfigs.config().redis(),
                TestConfigs.config().node(),
                TestConfigs.config().heartbeat(),
                TestConfigs.config().backend(),
                new PluginConfig.CommandConfig("vstats", 1000, 2),
                TestConfigs.config().permissions()
        );
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 4, List.of("alpha", "Beta", "delta", "gamma"), Map.of(), 1L)
        ));
        when(repository.loadSnapshot(config, redisManager)).thenReturn(snapshot);

        VStatsCommand command = new VStatsCommand(
                null,
                proxyServer,
                new AtomicReference<>(config),
                new AtomicReference<>(redisManager),
                repository,
                null,
                new MessageFormatter(),
                new AtomicBoolean(false)
        );
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source, "list", "public"));

        verifyPlainMessage(source, "There are 4 player(s): alpha, Beta ... +2 more");
    }

    @Test
    void executeReusesCachedSnapshotForShortCommandBursts() throws Exception {
        ProxyServer proxyServer = proxyServerThatRunsTasksImmediately();
        StatsRepository repository = mock(StatsRepository.class);
        RedisManager redisManager = mock(RedisManager.class);
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 1, List.of("alpha"), Map.of(), 1L)
        ));
        when(repository.loadSnapshot(TestConfigs.config(), redisManager)).thenReturn(snapshot);

        VStatsCommand command = new VStatsCommand(
                null,
                proxyServer,
                new AtomicReference<>(TestConfigs.config()),
                new AtomicReference<>(redisManager),
                repository,
                null,
                new MessageFormatter(),
                new AtomicBoolean(false)
        );

        command.execute(invocation(mock(CommandSource.class), "public"));
        command.execute(invocation(mock(CommandSource.class), "servers"));

        verify(repository, times(1)).loadSnapshot(TestConfigs.config(), redisManager);
    }

    @Test
    void executeRejectsPlayerWithoutViewPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.getPermissionValue("vstats.view")).thenReturn(Tristate.FALSE);
        SimpleCommand.Invocation invocation = invocation(source);

        command.execute(invocation);

        verifyPlainMessage(source, "You do not have permission to use this command.");
    }

    @Test
    void executeRejectsStaffStatsWithoutStaffPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.getPermissionValue("vstats.staff")).thenReturn(Tristate.FALSE);
        SimpleCommand.Invocation invocation = invocation(source, "staff");

        command.execute(invocation);

        verifyPlainMessage(source, "You do not have permission to view staff stats.");
    }

    @Test
    void executeRejectsStaffListWithoutStaffPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.getPermissionValue("vstats.list")).thenReturn(Tristate.TRUE);
        when(source.getPermissionValue("vstats.staff")).thenReturn(Tristate.FALSE);
        SimpleCommand.Invocation invocation = invocation(source, "list", "staff");

        command.execute(invocation);

        verifyPlainMessage(source, "You do not have permission to view staff player list.");
    }

    @Test
    void executeRejectsConcurrentReload() {
        VStatsCommand command = command(true);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "reload");

        command.execute(invocation);

        verifyPlainMessage(source, "[vstats] Reload is already in progress.");
    }

    @Test
    void suggestReturnsOnlyDocumentedTopLevelSubcommands() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"s"});

        assertEquals(List.of("staff", "servers"), command.suggest(invocation));
    }

    @Test
    void suggestReturnsHelpSubcommand() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"h"});

        assertEquals(List.of("help"), command.suggest(invocation));
    }

    @Test
    void suggestListReturnsPublicAndStaffAliases() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"list", ""});

        assertEquals(List.of("public", "staff"), command.suggest(invocation));
    }

    @Test
    void suggestListFiltersPublicAlias() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"list", "pu"});

        assertEquals(List.of("public"), command.suggest(invocation));
    }

    private VStatsCommand command(boolean reloadInProgress) {
        return new VStatsCommand(
                null,
                null,
                new AtomicReference<>(TestConfigs.config()),
                new AtomicReference<>(),
                null,
                null,
                new MessageFormatter(),
                new AtomicBoolean(reloadInProgress)
        );
    }

    private SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(arguments);
        return invocation;
    }

    private ProxyServer proxyServerThatRunsTasksImmediately() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.TaskBuilder taskBuilder = mock(Scheduler.TaskBuilder.class);
        when(proxyServer.getScheduler()).thenReturn(scheduler);
        when(scheduler.buildTask(any(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return taskBuilder;
        });
        when(taskBuilder.schedule()).thenReturn(mock(ScheduledTask.class));
        return proxyServer;
    }

    private void verifyPlainMessage(CommandSource source, String expected) {
        verify(source).sendMessage(argThat(component -> expected.equals(PLAIN_TEXT.serialize(component))));
    }
}
