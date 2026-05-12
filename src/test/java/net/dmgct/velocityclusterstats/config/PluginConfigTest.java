package net.dmgct.velocityclusterstats.config;

import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginConfigTest {
    @Test
    void storesRuntimeConfigSections() {
        PluginConfig config = TestConfigs.config();

        assertEquals("127.0.0.1", config.redis().host());
        assertEquals("prx01", config.node().id());
        assertEquals(5, config.heartbeat().intervalSeconds());
        assertEquals("unassigned", config.backend().unassignedName());
        assertEquals("vstats", config.command().primary());
        assertEquals(1000, config.command().snapshotCacheMillis());
        assertEquals(100, config.command().playerListLimit());
        assertEquals("vstats.view", config.permissions().view());
    }
}
