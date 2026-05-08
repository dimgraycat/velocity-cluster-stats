package net.dmgct.velocityclusterstats.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.dmgct.velocityclusterstats.TestConfigs;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VStatsCommandTest {
    @Test
    void executeRejectsUnknownSubcommandsWithoutScheduler() {
        VStatsCommand command = command(false);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "nodes");

        command.execute(invocation);

        verify(source).sendMessage(Component.text("Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]"));
    }

    @Test
    void executeRejectsListWithTooManyArguments() {
        VStatsCommand command = command(false);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "list", "prx01", "extra");

        command.execute(invocation);

        verify(source).sendMessage(Component.text("Usage: /vstats list [nodeId|staff]"));
    }

    @Test
    void executeRejectsPlayerWithoutViewPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.hasPermission("vstats.view")).thenReturn(false);
        SimpleCommand.Invocation invocation = invocation(source);

        command.execute(invocation);

        verify(source).sendMessage(Component.text("You do not have permission to use this command."));
    }

    @Test
    void executeRejectsStaffStatsWithoutStaffPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.hasPermission("vstats.staff")).thenReturn(false);
        SimpleCommand.Invocation invocation = invocation(source, "staff");

        command.execute(invocation);

        verify(source).sendMessage(Component.text("You do not have permission to view staff stats."));
    }

    @Test
    void executeRejectsStaffListWithoutStaffPermission() {
        VStatsCommand command = command(false);
        Player source = mock(Player.class);
        when(source.hasPermission("vstats.list")).thenReturn(true);
        when(source.hasPermission("vstats.staff")).thenReturn(false);
        SimpleCommand.Invocation invocation = invocation(source, "list", "staff");

        command.execute(invocation);

        verify(source).sendMessage(Component.text("You do not have permission to view staff player list."));
    }

    @Test
    void executeRejectsConcurrentReload() {
        VStatsCommand command = command(true);
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = invocation(source, "reload");

        command.execute(invocation);

        verify(source).sendMessage(Component.text("[vstats] Reload is already in progress."));
    }

    @Test
    void suggestReturnsOnlyDocumentedTopLevelSubcommands() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"s"});

        assertEquals(List.of("staff", "servers"), command.suggest(invocation));
    }

    @Test
    void suggestListOnlyReturnsStaffAlias() {
        VStatsCommand command = command(false);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"list", "st"});

        assertEquals(List.of("staff"), command.suggest(invocation));
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
}
