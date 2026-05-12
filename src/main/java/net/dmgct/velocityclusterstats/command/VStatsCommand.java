package net.dmgct.velocityclusterstats.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dmgct.velocityclusterstats.VelocityClusterStatsPlugin;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import net.dmgct.velocityclusterstats.redis.StatsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Velocity command implementation for the fixed {@code /vstats} command set.
 */
public final class VStatsCommand implements SimpleCommand {
    private static final Component REDIS_ERROR = Component.text("[stats] Redis connection error.", NamedTextColor.RED);
    private static final Component SNAPSHOT_LOADING = Component.text(
            "[stats] Snapshot is loading. Try again shortly.",
            NamedTextColor.YELLOW
    );

    private final VelocityClusterStatsPlugin plugin;
    private final ProxyServer proxyServer;
    private final AtomicReference<PluginConfig> configRef;
    private final AtomicReference<RedisManager> redisManagerRef;
    private final StatsRepository statsRepository;
    private final ReloadService reloadService;
    private final MessageFormatter formatter;
    private final AtomicBoolean reloadInProgress;
    private final AtomicReference<CachedSnapshot> snapshotCache = new AtomicReference<>();
    private final AtomicReference<SnapshotLoad> snapshotLoadInProgress = new AtomicReference<>();
    private final AtomicLong snapshotGeneration = new AtomicLong();

    /**
     * Creates the command handler.
     *
     * @param plugin plugin instance used for scheduler ownership
     * @param proxyServer Velocity proxy server
     * @param configRef current config reference
     * @param redisManagerRef current Redis manager reference
     * @param statsRepository Redis snapshot reader
     * @param reloadService config reload service
     * @param formatter command output formatter
     * @param reloadInProgress reload guard
     */
    public VStatsCommand(
            VelocityClusterStatsPlugin plugin,
            ProxyServer proxyServer,
            AtomicReference<PluginConfig> configRef,
            AtomicReference<RedisManager> redisManagerRef,
            StatsRepository statsRepository,
            ReloadService reloadService,
            MessageFormatter formatter,
            AtomicBoolean reloadInProgress
    ) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.configRef = configRef;
        this.redisManagerRef = redisManagerRef;
        this.statsRepository = statsRepository;
        this.reloadService = reloadService;
        this.formatter = formatter;
        this.reloadInProgress = reloadInProgress;
    }

    /**
     * Executes the requested {@code /vstats} subcommand.
     *
     * @param invocation Velocity command invocation
     */
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        PluginConfig config = configRef.get();

        if (args.length == 0) {
            requireAndRun(source, config.permissions().view(), permissionError("You do not have permission to use this command."),
                    snapshot -> formatter.formatRootComponent(snapshot));
            return;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "public" -> {
                if (args.length != 1) {
                    sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
                    return;
                }
                requireAndRun(source, config.permissions().view(), permissionError("You do not have permission to use this command."),
                        snapshot -> formatter.formatPublicComponent(snapshot));
            }
            case "staff" -> {
                if (args.length != 1) {
                    sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
                    return;
                }
                requireAndRun(source, config.permissions().staff(), permissionError("You do not have permission to view staff stats."),
                        snapshot -> formatter.formatStaffComponent(snapshot));
            }
            case "servers" -> {
                if (args.length != 1) {
                    sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
                    return;
                }
                requireAndRun(source, config.permissions().view(), permissionError("You do not have permission to use this command."),
                        snapshot -> formatter.formatServersComponent(snapshot));
            }
            case "list" -> handleList(source, args, config);
            case "reload" -> handleReload(source, args, config);
            case "help" -> handleHelp(source, args, config);
            default -> sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
        }
    }

    private void handleList(CommandSource source, String[] args, PluginConfig config) {
        if (!hasPermission(source, config.permissions().list())) {
            send(source, permissionError("You do not have permission to use this command."));
            return;
        }
        if (args.length > 2) {
            send(source, Component.text("Usage: /vstats list [nodeId|public|staff]", NamedTextColor.YELLOW));
            return;
        }

        String target = args.length == 2 ? args[1] : null;
        if ("staff".equalsIgnoreCase(target) && !hasPermission(source, config.permissions().staff())) {
            send(source, permissionError("You do not have permission to view staff player list."));
            return;
        }

        runWithSnapshot(source, snapshot -> {
            List<String> players;
            if (target == null) {
                players = hasPermission(source, config.permissions().staff())
                        ? snapshot.allPlayers()
                        : snapshot.publicPlayers();
            } else if ("public".equalsIgnoreCase(target)) {
                players = snapshot.publicPlayers();
            } else if ("staff".equalsIgnoreCase(target)) {
                players = snapshot.staffPlayers();
            } else {
                NodeSnapshot node = snapshot.nodeById(target);
                if (node == null) {
                    return Component.text("Velocity node not found: " + target, NamedTextColor.RED);
                }
                if (PluginConfig.GROUP_STAFF.equals(node.group()) && !hasPermission(source, config.permissions().staff())) {
                    return permissionError("You do not have permission to view staff player list.");
                }
                players = new ArrayList<>(node.players());
                players.sort(String.CASE_INSENSITIVE_ORDER);
            }
            return formatter.formatPlayerListComponent(players, config.command().playerListLimit());
        });
    }

    private void handleReload(CommandSource source, String[] args, PluginConfig config) {
        if (args.length != 1) {
            sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
            return;
        }
        if (!hasPermission(source, config.permissions().reload())) {
            send(source, permissionError("You do not have permission to use this command."));
            return;
        }
        if (!reloadInProgress.compareAndSet(false, true)) {
            send(source, Component.text("[vstats] Reload is already in progress.", NamedTextColor.YELLOW));
            return;
        }

        send(source, Component.text("[vstats] Reloading config...", NamedTextColor.YELLOW));
        proxyServer.getScheduler().buildTask(plugin, () -> {
            try {
                ReloadService.ReloadResult result = reloadService.load();
                if (result.success()) {
                    plugin.applyReload(result.config(), result.redisManager());
                }
                send(source, reloadResultMessage(result));
            } finally {
                reloadInProgress.set(false);
            }
        }).schedule();
    }

    private void handleHelp(CommandSource source, String[] args, PluginConfig config) {
        if (args.length != 1) {
            sendError(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]");
            return;
        }

        List<String> commands = new ArrayList<>();
        if (hasPermission(source, config.permissions().view())) {
            commands.add("/vstats");
            commands.add("/vstats public");
        }
        if (hasPermission(source, config.permissions().staff())) {
            commands.add("/vstats staff");
        }
        if (hasPermission(source, config.permissions().view())) {
            commands.add("/vstats servers");
        }
        if (hasPermission(source, config.permissions().list())) {
            commands.add("/vstats list");
            commands.add("/vstats list public");
            commands.add("/vstats list <nodeId>");
            if (hasPermission(source, config.permissions().staff())) {
                commands.add("/vstats list staff");
            }
        }
        if (hasPermission(source, config.permissions().reload())) {
            commands.add("/vstats reload");
        }
        commands.add("/vstats help");

        send(source, formatter.formatHelpComponent(commands));
    }

    private void requireAndRun(CommandSource source, String permission, Component error, SnapshotFormatter snapshotFormatter) {
        if (!hasPermission(source, permission)) {
            send(source, error);
            return;
        }
        runWithSnapshot(source, snapshotFormatter);
    }

    private void runWithSnapshot(CommandSource source, SnapshotFormatter snapshotFormatter) {
        PluginConfig config = configRef.get();
        RedisManager redisManager = redisManagerRef.get();
        long generation = snapshotGeneration.get();
        CachedSnapshot cachedSnapshot = snapshotCache.get();
        long now = System.currentTimeMillis();
        if (cachedSnapshot != null
                && cachedSnapshot.generation() == generation
                && now <= cachedSnapshot.expiresAtMillis()) {
            send(source, snapshotFormatter.format(cachedSnapshot.snapshot()));
            return;
        }

        SnapshotLoad existingLoad = snapshotLoadInProgress.get();
        if (existingLoad != null && existingLoad.generation() == generation) {
            send(source, SNAPSHOT_LOADING);
            return;
        }

        CompletableFuture<ClusterSnapshot> newLoad = new CompletableFuture<>();
        SnapshotLoad snapshotLoad = new SnapshotLoad(newLoad, generation);
        if (!snapshotLoadInProgress.compareAndSet(existingLoad, snapshotLoad)) {
            SnapshotLoad concurrentLoad = snapshotLoadInProgress.get();
            if (concurrentLoad != null && concurrentLoad.generation() == generation) {
                send(source, SNAPSHOT_LOADING);
            } else {
                runWithSnapshot(source, snapshotFormatter);
            }
            return;
        }
        attachSnapshotResponse(newLoad, source, snapshotFormatter);
        proxyServer.getScheduler().buildTask(plugin, () -> {
            try {
                ClusterSnapshot snapshot = statsRepository.loadSnapshot(config, redisManager);
                long cacheMillis = config.command().snapshotCacheMillis();
                if (snapshotGeneration.get() == generation) {
                    if (cacheMillis > 0) {
                        snapshotCache.set(new CachedSnapshot(snapshot, System.currentTimeMillis() + cacheMillis, generation));
                    }
                    newLoad.complete(snapshot);
                } else {
                    newLoad.completeExceptionally(new StaleSnapshotException());
                }
            } catch (StatsRepository.StatsUnavailableException exception) {
                newLoad.completeExceptionally(exception);
            } finally {
                snapshotLoadInProgress.compareAndSet(snapshotLoad, null);
            }
        }).schedule();
    }

    private void attachSnapshotResponse(
            CompletableFuture<ClusterSnapshot> load,
            CommandSource source,
            SnapshotFormatter snapshotFormatter
    ) {
        load.whenComplete((snapshot, exception) -> sendSnapshotResult(source, snapshotFormatter, snapshot, exception));
    }

    private void sendSnapshotResult(
            CommandSource source,
            SnapshotFormatter snapshotFormatter,
            ClusterSnapshot snapshot,
            Throwable exception
    ) {
        if (exception != null) {
            send(source, REDIS_ERROR);
            return;
        }
        send(source, snapshotFormatter.format(snapshot));
    }

    private boolean hasPermission(CommandSource source, String permission) {
        return !(source instanceof Player) || source.getPermissionValue(permission) != Tristate.FALSE;
    }

    private Component permissionError(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private void sendError(CommandSource source, String message) {
        send(source, Component.text(message, NamedTextColor.RED));
    }

    private Component reloadResultMessage(ReloadService.ReloadResult result) {
        if (!result.success()) {
            return Component.text(result.message(), NamedTextColor.RED);
        }
        if (!result.redisAvailable()) {
            return Component.text(result.message(), NamedTextColor.YELLOW);
        }
        return Component.text(result.message(), NamedTextColor.GREEN);
    }

    private void send(CommandSource source, Component message) {
        source.sendMessage(message);
    }

    /**
     * Clears cached command snapshots after config or Redis settings change.
     */
    public CompletableFuture<?> clearSnapshotCache() {
        snapshotGeneration.incrementAndGet();
        snapshotCache.set(null);
        SnapshotLoad previousLoad = snapshotLoadInProgress.getAndSet(null);
        return previousLoad == null ? CompletableFuture.completedFuture(null) : previousLoad.future();
    }

    /**
     * Suggests only the documented command arguments.
     *
     * @param invocation Velocity command invocation
     * @return completion candidates
     */
    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("public", "staff", "servers", "list", "reload", "help");
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("public", "staff", "servers", "list", "reload", "help").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("public", "staff").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    @FunctionalInterface
    private interface SnapshotFormatter {
        Component format(ClusterSnapshot snapshot);
    }

    private record CachedSnapshot(ClusterSnapshot snapshot, long expiresAtMillis, long generation) {
    }

    private record SnapshotLoad(CompletableFuture<ClusterSnapshot> future, long generation) {
    }

    private static final class StaleSnapshotException extends Exception {
    }
}
