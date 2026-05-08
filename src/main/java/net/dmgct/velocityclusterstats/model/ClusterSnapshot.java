package net.dmgct.velocityclusterstats.model;

import net.dmgct.velocityclusterstats.config.PluginConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated cluster-wide snapshot derived from active node snapshots.
 *
 * @param nodes active Velocity node snapshots
 * @param servers backend server counts aggregated by node group
 */
public record ClusterSnapshot(List<NodeSnapshot> nodes, List<ServerGroupCount> servers) {
    private static final Comparator<String> CASE_INSENSITIVE = String.CASE_INSENSITIVE_ORDER;

    /**
     * Builds a cluster snapshot and derives backend server totals from node snapshots.
     *
     * @param nodes active node snapshots
     * @return aggregated cluster snapshot
     */
    public static ClusterSnapshot fromNodes(List<NodeSnapshot> nodes) {
        List<NodeSnapshot> sortedNodes = nodes.stream()
                .sorted(Comparator.comparing(NodeSnapshot::id, CASE_INSENSITIVE))
                .toList();

        Map<String, int[]> serverCounts = new HashMap<>();
        for (NodeSnapshot node : sortedNodes) {
            boolean staff = PluginConfig.GROUP_STAFF.equals(node.group());
            for (Map.Entry<String, Integer> entry : node.backendCounts().entrySet()) {
                int[] counts = serverCounts.computeIfAbsent(entry.getKey(), ignored -> new int[2]);
                if (staff) {
                    counts[1] += entry.getValue();
                } else {
                    counts[0] += entry.getValue();
                }
            }
        }

        List<ServerGroupCount> servers = serverCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CASE_INSENSITIVE))
                .map(entry -> new ServerGroupCount(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();

        return new ClusterSnapshot(sortedNodes, servers);
    }

    /**
     * Returns active public nodes sorted by node id.
     *
     * @return public node snapshots
     */
    public List<NodeSnapshot> publicNodes() {
        return nodes.stream()
                .filter(node -> PluginConfig.GROUP_PUBLIC.equals(node.group()))
                .sorted(Comparator.comparing(NodeSnapshot::id, CASE_INSENSITIVE))
                .toList();
    }

    /**
     * Returns active staff nodes sorted by node id.
     *
     * @return staff node snapshots
     */
    public List<NodeSnapshot> staffNodes() {
        return nodes.stream()
                .filter(node -> PluginConfig.GROUP_STAFF.equals(node.group()))
                .sorted(Comparator.comparing(NodeSnapshot::id, CASE_INSENSITIVE))
                .toList();
    }

    /**
     * Returns active public node count.
     *
     * @return public node count
     */
    public int publicNodeCount() {
        return publicNodes().size();
    }

    /**
     * Returns active staff node count.
     *
     * @return staff node count
     */
    public int staffNodeCount() {
        return staffNodes().size();
    }

    /**
     * Returns players connected through public nodes.
     *
     * @return public player count
     */
    public int publicPlayerCount() {
        return publicNodes().stream().mapToInt(NodeSnapshot::playerCount).sum();
    }

    /**
     * Returns players connected through staff nodes.
     *
     * @return staff player count
     */
    public int staffPlayerCount() {
        return staffNodes().stream().mapToInt(NodeSnapshot::playerCount).sum();
    }

    /**
     * Returns total players across public and staff nodes.
     *
     * @return total player count
     */
    public int totalPlayerCount() {
        return publicPlayerCount() + staffPlayerCount();
    }

    /**
     * Returns public player names sorted case-insensitively.
     *
     * @return public player names
     */
    public List<String> publicPlayers() {
        List<String> players = new ArrayList<>();
        publicNodes().forEach(node -> players.addAll(node.players()));
        players.sort(CASE_INSENSITIVE);
        return players;
    }

    /**
     * Returns staff player names sorted case-insensitively.
     *
     * @return staff player names
     */
    public List<String> staffPlayers() {
        List<String> players = new ArrayList<>();
        staffNodes().forEach(node -> players.addAll(node.players()));
        players.sort(CASE_INSENSITIVE);
        return players;
    }

    /**
     * Returns all player names sorted case-insensitively.
     *
     * @return all player names
     */
    public List<String> allPlayers() {
        List<String> players = new ArrayList<>();
        nodes.forEach(node -> players.addAll(node.players()));
        players.sort(CASE_INSENSITIVE);
        return players;
    }

    /**
     * Finds an active node by id.
     *
     * @param nodeId node id to find
     * @return matching node snapshot, or {@code null} if absent
     */
    public NodeSnapshot nodeById(String nodeId) {
        return nodes.stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }
}
