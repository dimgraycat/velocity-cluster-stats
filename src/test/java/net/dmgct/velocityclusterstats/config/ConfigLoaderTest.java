package net.dmgct.velocityclusterstats.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesAndLoadsDefaultConfig() throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);

        assertTrue(loader.ensureDefaultConfig());
        PluginConfig config = loader.load();

        assertEquals("127.0.0.1", config.redis().host());
        assertEquals(6379, config.redis().port());
        assertEquals("vstats", config.redis().keyPrefix());
        assertEquals("prx01", config.node().id());
        assertEquals(PluginConfig.GROUP_PUBLIC, config.node().group());
        assertEquals("vstats", config.command().primary());
    }
}
