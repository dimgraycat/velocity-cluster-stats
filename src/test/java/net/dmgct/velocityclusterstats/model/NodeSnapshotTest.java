package net.dmgct.velocityclusterstats.model;

import net.dmgct.velocityclusterstats.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeSnapshotTest {
    @Test
    void exposesRedisNodeSnapshotValues() {
        NodeSnapshot snapshot = new NodeSnapshot(
                "prx01",
                PluginConfig.GROUP_PUBLIC,
                2,
                List.of("alpha", "beta"),
                Map.of("lobby", 2),
                123L
        );

        assertEquals("prx01", snapshot.id());
        assertEquals(2, snapshot.playerCount());
        assertEquals(2, snapshot.backendCounts().get("lobby"));
        assertEquals(123L, snapshot.updatedAt());
    }
}
