package net.dmgct.velocityclusterstats.command;

import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import net.dmgct.velocityclusterstats.model.ServerGroupCount;

import java.util.List;

/**
 * Formats cluster snapshots into the exact plain-text command output required by the spec.
 */
public final class MessageFormatter {
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
        StringBuilder builder = new StringBuilder();
        builder.append("[stats]\n");
        builder.append("Active Velocity Nodes: ")
                .append(snapshot.nodes().size())
                .append("  public=")
                .append(snapshot.publicNodeCount());
        if (snapshot.staffNodeCount() > 0) {
            builder.append(" / staff=").append(snapshot.staffNodeCount());
        }
        builder.append('\n');
        builder.append("Total Players: ").append(snapshot.totalPlayerCount()).append("\n\n");
        builder.append(formatPublic(snapshot)).append("\n\n");
        if (snapshot.staffNodeCount() > 0) {
            builder.append(formatStaff(snapshot)).append("\n\n");
        }
        builder.append(formatServers(snapshot));
        return builder.toString();
    }

    /**
     * Formats the {@code /vstats public} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted public node response
     */
    public String formatPublic(ClusterSnapshot snapshot) {
        StringBuilder builder = new StringBuilder("[Public]\n");
        for (NodeSnapshot node : snapshot.publicNodes()) {
            builder.append(node.id()).append(": ").append(node.playerCount()).append(" players\n");
        }
        builder.append("Total: ").append(snapshot.publicPlayerCount()).append(" players");
        return builder.toString();
    }

    /**
     * Formats the {@code /vstats staff} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted staff response
     */
    public String formatStaff(ClusterSnapshot snapshot) {
        return "[Staff]\nstaff: " + snapshot.staffPlayerCount() + " players";
    }

    /**
     * Formats the {@code /vstats servers} block.
     *
     * @param snapshot cluster snapshot to display
     * @return formatted backend server response
     */
    public String formatServers(ClusterSnapshot snapshot) {
        boolean showStaff = snapshot.staffNodeCount() > 0;
        StringBuilder builder = new StringBuilder("[Servers]\n");
        for (ServerGroupCount server : snapshot.servers()) {
            builder.append(server.serverName())
                    .append(": Public ")
                    .append(server.publicPlayers());
            if (showStaff) {
                builder.append(", Staff ").append(server.staffPlayers());
            }
            builder.append('\n');
        }
        builder.append("Total: ").append(snapshot.totalPlayerCount()).append(" players");
        if (showStaff) {
            builder.append(", Public ")
                    .append(snapshot.publicPlayerCount())
                    .append(", Staff ")
                    .append(snapshot.staffPlayerCount());
        }
        return builder.toString();
    }

    /**
     * Formats player names in whitelist-style output.
     *
     * @param players sorted player names
     * @return formatted player list response
     */
    public String formatPlayerList(List<String> players) {
        if (players.isEmpty()) {
            return "There are 0 player(s):";
        }
        return "There are " + players.size() + " player(s): " + String.join(", ", players);
    }
}
