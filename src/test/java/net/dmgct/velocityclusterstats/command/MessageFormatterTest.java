package net.dmgct.velocityclusterstats.command;

import net.dmgct.velocityclusterstats.config.PluginConfig;
import net.dmgct.velocityclusterstats.model.ClusterSnapshot;
import net.dmgct.velocityclusterstats.model.NodeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    private final MessageFormatter formatter = new MessageFormatter();

    @Test
    void rootOmitsStaffSectionsWhenNoStaffNodeExists() {
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("prx02", PluginConfig.GROUP_PUBLIC, 16, List.of("bbb"), Map.of("lobby", 16), 1L),
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 13, List.of("aaa"), Map.of("game01", 9, "main", 4), 1L),
                new NodeSnapshot("prx03", PluginConfig.GROUP_PUBLIC, 13, List.of("ccc"), Map.of("main", 13), 1L)
        ));

        assertEquals("""
                [stats]
                Active Velocity Nodes: 3  public=3
                Total Players: 42

                [Public]
                prx01: 13 players
                prx02: 16 players
                prx03: 13 players
                Total: 42 players

                [Servers]
                game01: Public 9
                lobby: Public 16
                main: Public 17
                Total: 42 players""", formatter.formatRoot(snapshot));
    }

    @Test
    void rootIncludesStaffSectionsWhenStaffNodeExists() {
        ClusterSnapshot snapshot = ClusterSnapshot.fromNodes(List.of(
                new NodeSnapshot("prx01", PluginConfig.GROUP_PUBLIC, 42, List.of("aaa"), Map.of("lobby", 42), 1L),
                new NodeSnapshot("staff", PluginConfig.GROUP_STAFF, 3, List.of("staffA"), Map.of("lobby", 1, "main", 2), 1L)
        ));

        assertEquals("""
                [stats]
                Active Velocity Nodes: 2  public=1 / staff=1
                Total Players: 45

                [Public]
                prx01: 42 players
                Total: 42 players

                [Staff]
                staff: 3 players

                [Servers]
                lobby: Public 42, Staff 1
                main: Public 0, Staff 2
                Total: 45 players, Public 42, Staff 3""", formatter.formatRoot(snapshot));
    }

    @Test
    void playerListUsesWhitelistStyle() {
        assertEquals("There are 0 player(s):", formatter.formatPlayerList(List.of()));
        assertEquals("There are 2 player(s): alpha, Beta", formatter.formatPlayerList(List.of("alpha", "Beta")));
    }
}
