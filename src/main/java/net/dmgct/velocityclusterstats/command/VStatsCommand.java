package net.dmgct.velocityclusterstats.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dmgct.velocityclusterstats.VelocityClusterStatsPlugin;
import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import net.dmgct.velocityclusterstats.redis.RedisManager;
import net.dmgct.velocityclusterstats.redis.StatsRepository;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Velocity command implementation for the fixed {@code /vstats} command set.
 */
public final class VStatsCommand implements SimpleCommand {
    private static final String REDIS_ERROR = "[stats] Redis connection error.";

    private final VelocityClusterStatsPlugin plugin;
    private final ProxyServer proxyServer;
    private final AtomicReference<PluginConfig> configRef;
    private final AtomicReference<RedisManager> redisManagerRef;
    private final StatsRepository statsRepository;
    private final ReloadService reloadService;
    private final MessageFormatter formatter;
    private final AtomicBoolean reloadInProgress;

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
            requireAndRun(source, config.permissions().view(), "You do not have permission to use this command.",
                    snapshot -> formatter.formatRoot(snapshot));
            return;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "public" -> {
                if (args.length != 1) {
                    send(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]");
                    return;
                }
                requireAndRun(source, config.permissions().view(), "You do not have permission to use this command.",
                        snapshot -> formatter.formatPublic(snapshot));
            }
            case "staff" -> {
                if (args.length != 1) {
                    send(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]");
                    return;
                }
                requireAndRun(source, config.permissions().staff(), "You do not have permission to view staff stats.",
                        snapshot -> formatter.formatStaff(snapshot));
            }
            case "servers" -> {
                if (args.length != 1) {
                    send(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]");
                    return;
                }
                requireAndRun(source, config.permissions().view(), "You do not have permission to use this command.",
                        snapshot -> formatter.formatServers(snapshot));
            }
            case "list" -> handleList(source, args, config);
            case "reload" -> handleReload(source, args, config);
            default -> send(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]");
        }
    }

    private void handleList(CommandSource source, String[] args, PluginConfig config) {
        if (!hasPermission(source, config.permissions().list())) {
            send(source, "You do not have permission to use this command.");
            return;
        }
        if (args.length > 2) {
            send(source, "Usage: /vstats list [nodeId|staff]");
            return;
        }

        String target = args.length == 2 ? args[1] : null;
        if ("staff".equalsIgnoreCase(target) && !hasPermission(source, config.permissions().staff())) {
            send(source, "You do not have permission to view staff player list.");
            return;
        }

        runWithSnapshot(source, snapshot -> {
            List<String> players;
            if (target == null) {
                players = hasPermission(source, config.permissions().staff())
                        ? snapshot.allPlayers()
                        : snapshot.publicPlayers();
            } else if ("staff".equalsIgnoreCase(target)) {
                players = snapshot.staffPlayers();
            } else {
                NodeSnapshot node = snapshot.nodeById(target);
                if (node == null) {
                    return "Velocity node not found: " + target;
                }
                if (PluginConfig.GROUP_STAFF.equals(node.group()) && !hasPermission(source, config.permissions().staff())) {
                    return "You do not have permission to view staff player list.";
                }
                players = new ArrayList<>(node.players());
                players.sort(String.CASE_INSENSITIVE_ORDER);
            }
            return formatter.formatPlayerList(players);
        });
    }

    private void handleReload(CommandSource source, String[] args, PluginConfig config) {
        if (args.length != 1) {
            send(source, "Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload]");
            return;
        }
        if (!hasPermission(source, config.permissions().reload())) {
            send(source, "You do not have permission to use this command.");
            return;
        }
        if (!reloadInProgress.compareAndSet(false, true)) {
            send(source, "[vstats] Reload is already in progress.");
            return;
        }

        send(source, "[vstats] Reloading config...");
        proxyServer.getScheduler().buildTask(plugin, () -> {
            try {
                ReloadService.ReloadResult result = reloadService.load();
                if (result.success()) {
                    plugin.applyReload(result.config(), result.redisManager());
                }
                send(source, result.message());
            } finally {
                reloadInProgress.set(false);
            }
        }).schedule();
    }

    private void requireAndRun(CommandSource source, String permission, String error, SnapshotFormatter snapshotFormatter) {
        if (!hasPermission(source, permission)) {
            send(source, error);
            return;
        }
        runWithSnapshot(source, snapshotFormatter);
    }

    private void runWithSnapshot(CommandSource source, SnapshotFormatter snapshotFormatter) {
        PluginConfig config = configRef.get();
        RedisManager redisManager = redisManagerRef.get();

        proxyServer.getScheduler().buildTask(plugin, () -> {
            try {
                ClusterSnapshot snapshot = statsRepository.loadSnapshot(config, redisManager);
                send(source, snapshotFormatter.format(snapshot));
            } catch (StatsRepository.StatsUnavailableException exception) {
                send(source, REDIS_ERROR);
            }
        }).schedule();
    }

    private boolean hasPermission(CommandSource source, String permission) {
        return !(source instanceof Player) || source.hasPermission(permission);
    }

    private void send(CommandSource source, String message) {
        source.sendMessage(Component.text(message));
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
            return List.of("public", "staff", "servers", "list", "reload");
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("public", "staff", "servers", "list", "reload").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            Set<String> suggestions = Set.of("staff");
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    @FunctionalInterface
    private interface SnapshotFormatter {
        String format(ClusterSnapshot snapshot);
    }
}
