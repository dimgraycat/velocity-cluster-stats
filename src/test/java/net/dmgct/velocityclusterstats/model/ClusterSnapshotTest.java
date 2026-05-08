package net.dmgct.velocityclusterstats.model;

import net.dmgct.velocityclusterstats.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClusterSnapshotTest {
    @Test
    void aggregatesNodesPlayersAndServers() {
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("staff", PluginConfig.GROUP_STAFF, 1, List.of("StaffA"), Map.of("main", 1), 1L),
                new NodeSnapshot("prx02", PluginConfig.GROUP_PUBLIC, 1, List.of("beta"), Map.of("lobby", 1), 1L),
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 1, List.of("Alpha"), Map.of("lobby", 1), 1L)
        ));

        assertEquals(2, snapshot.publicNodeCount());
        assertEquals(1, snapshot.staffNodeCount());
        assertEquals(3, snapshot.totalPlayerCount());
        assertEquals(List.of("Alpha", "beta"), snapshot.publicPlayers());
        assertEquals("prx01", snapshot.publicNodes().getFirst().id());
        assertEquals(2, snapshot.servers().getFirst().publicPlayers());
        assertEquals("staff", snapshot.nodeById("staff").id());
        assertNull(snapshot.nodeById("missing"));
    }
}
