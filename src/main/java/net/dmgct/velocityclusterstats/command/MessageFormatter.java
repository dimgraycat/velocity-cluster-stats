package net.dmgct.velocityclusterstats.command;

import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import net.dmgct.velocityclusterstats.model.ServerGroupCount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;

/**
 * Formats cluster snapshots into colored command output.
 */
public final class MessageFormatter {
    private static final String ROOT_SEPARATOR = "========================================";
    private static final NamedTextColor HEADER = NamedTextColor.GOLD;
    private static final NamedTextColor PUBLIC = NamedTextColor.GREEN;
    private static final NamedTextColor STAFF = NamedTextColor.LIGHT_PURPLE;
    private static final NamedTextColor VALUE = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor NAME = NamedTextColor.WHITE;

    /**
     * Creates a formatter.
     */
    public MessageFormatter() {
    }

    /**
     * Formats the default {@code /vstats} output.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted command response
     */
    public String formatRoot(ClusterSnapshot snapshot) {
        return plain(formatRootComponent(snapshot));
    }

    /**
     * Formats the default {@code /vstats} output as a colored component.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted command response
     */
    public Component formatRootComponent(ClusterSnapshot snapshot) {
        TextComponent.Builder builder = Component.text();
        appendLine(builder, ROOT_SEPARATOR, LABEL);
        appendLine(builder, "[Stats]", HEADER);
        append(builder, "Active Velocity Nodes: ", LABEL);
        append(builder, String.valueOf(snapshot.nodes().size()), VALUE);
        append(builder, ",  ", LABEL);
        append(builder, "public=", PUBLIC);
        append(builder, String.valueOf(snapshot.publicNodeCount()), VALUE);
        if (snapshot.staffNodeCount() > 0) {
            append(builder, " / ", LABEL);
            append(builder, "staff=", STAFF);
            append(builder, String.valueOf(snapshot.staffNodeCount()), VALUE);
        }
        newline(builder);
        append(builder, "Total Players: ", LABEL);
        append(builder, String.valueOf(snapshot.totalPlayerCount()), VALUE);
        newline(builder);
        newline(builder);
        builder.append(formatPublicComponent(snapshot));
        newline(builder);
        newline(builder);
        if (snapshot.staffNodeCount() > 0) {
            builder.append(formatStaffComponent(snapshot));
            newline(builder);
            newline(builder);
        }
        builder.append(formatServersComponent(snapshot));
        return builder.build();
    }

    /**
     * Formats the {@code /vstats public} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted public node response
     */
    public String formatPublic(ClusterSnapshot snapshot) {
        return plain(formatPublicComponent(snapshot));
    }

    /**
     * Formats the {@code /vstats public} block as a colored component.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted public node response
     */
    public Component formatPublicComponent(ClusterSnapshot snapshot) {
        TextComponent.Builder builder = Component.text();
        appendLine(builder, "[Public]", PUBLIC);
        for (NodeSnapshot node : snapshot.publicNodes()) {
            append(builder, node.id(), NAME);
            append(builder, ": ", LABEL);
            append(builder, String.valueOf(node.playerCount()), VALUE);
            appendLine(builder, " players", LABEL);
        }
        append(builder, "Total: ", LABEL);
        append(builder, String.valueOf(snapshot.publicPlayerCount()), VALUE);
        append(builder, " players", LABEL);
        return builder.build();
    }

    /**
     * Formats the {@code /vstats staff} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted staff response
     */
    public String formatStaff(ClusterSnapshot snapshot) {
        return plain(formatStaffComponent(snapshot));
    }

    /**
     * Formats the {@code /vstats staff} block as a colored component.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted staff response
     */
    public Component formatStaffComponent(ClusterSnapshot snapshot) {
        TextComponent.Builder builder = Component.text();
        appendLine(builder, "[Staff]", STAFF);
        append(builder, "staff", NAME);
        append(builder, ": ", LABEL);
        append(builder, String.valueOf(snapshot.staffPlayerCount()), VALUE);
        append(builder, " players", LABEL);
        return builder.build();
    }

    /**
     * Formats the {@code /vstats servers} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted backend server response
     */
    public String formatServers(ClusterSnapshot snapshot) {
        return plain(formatServersComponent(snapshot));
    }

    /**
     * Formats the {@code /vstats servers} block as a colored component.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted backend server response
     */
    public Component formatServersComponent(ClusterSnapshot snapshot) {
        boolean showStaff = snapshot.staffNodeCount() > 0;
        TextComponent.Builder builder = Component.text();
        appendLine(builder, "[Servers]", HEADER);
        for (ServerGroupCount server : snapshot.servers()) {
            append(builder, server.serverName(), NAME);
            append(builder, ": ", LABEL);
            append(builder, "Public ", PUBLIC);
            append(builder, String.valueOf(server.publicPlayers()), VALUE);
            if (showStaff) {
                append(builder, ", ", LABEL);
                append(builder, "Staff ", STAFF);
                append(builder, String.valueOf(server.staffPlayers()), VALUE);
            }
            newline(builder);
        }
        append(builder, "Total: ", LABEL);
        append(builder, String.valueOf(snapshot.totalPlayerCount()), VALUE);
        append(builder, " players", LABEL);
        if (showStaff) {
            append(builder, ", ", LABEL);
            append(builder, "Public ", PUBLIC);
            append(builder, String.valueOf(snapshot.publicPlayerCount()), VALUE);
            append(builder, ", ", LABEL);
            append(builder, "Staff ", STAFF);
            append(builder, String.valueOf(snapshot.staffPlayerCount()), VALUE);
        }
        return builder.build();
    }

    /**
     * Formats player names in whitelist-style output.
     *
     * @param players sorted player names
     * @return formatted player list response
     */
    public String formatPlayerList(List<String> players) {
        return plain(formatPlayerListComponent(players));
    }

    /**
     * Formats player names in whitelist-style output as a colored component.
     *
     * @param players sorted player names
     * @return formatted player list response
     */
    public Component formatPlayerListComponent(List<String> players) {
        TextComponent.Builder builder = Component.text();
        if (players.isEmpty()) {
            append(builder, "There are ", LABEL);
            append(builder, "0", VALUE);
            append(builder, " player(s):", LABEL);
            return builder.build();
        }
        append(builder, "There are ", LABEL);
        append(builder, String.valueOf(players.size()), VALUE);
        append(builder, " player(s): ", LABEL);
        append(builder, String.join(", ", players), NAME);
        return builder.build();
    }

    /**
     * Formats help command entries in a console-friendly style.
     *
     * @param commands command lines to display
     * @return formatted help response
     */
    public Component formatHelpComponent(List<String> commands) {
        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < commands.size(); i++) {
            append(builder, "> ", LABEL);
            append(builder, commands.get(i), NAME);
            if (i + 1 < commands.size()) {
                newline(builder);
            }
        }
        return builder.build();
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void append(TextComponent.Builder builder, String text, NamedTextColor color) {
        builder.append(Component.text(text, color));
    }

    private static void appendLine(TextComponent.Builder builder, String text, NamedTextColor color) {
        append(builder, text, color);
        newline(builder);
    }

    private static void newline(TextComponent.Builder builder) {
        builder.append(Component.newline());
    }
}
