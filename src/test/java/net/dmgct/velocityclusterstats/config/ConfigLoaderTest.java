package net.dmgct.velocityclusterstats.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(1000, config.command().snapshotCacheMillis());
        assertEquals(100, config.command().playerListLimit());
    }

    @Test
    void rejectsFractionalYamlNumbersForIntegerValues() throws Exception {
        writeConfig("1.9");

        assertThrows(ConfigLoader.ConfigValidationException.class, () -> new ConfigLoader(tempDir).load());
    }

    @Test
    void rejectsHugeYamlIntegersBeforeIntegerOverflow() throws Exception {
        writeConfig("9223372036854775808");

        assertThrows(ConfigLoader.ConfigValidationException.class, () -> new ConfigLoader(tempDir).load());
    }

    private void writeConfig(String redisPort) throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                redis:
                  host: "127.0.0.1"
                  port: %s
                  password: ""
                  database: 0
                  key-prefix: "vstats"
                  connection-timeout-millis: 1000
                  socket-timeout-millis: 1000
                  failure-log-cooldown-seconds: 60
                node:
                  id: "prx01"
                  group: "public"
                heartbeat:
                  interval-seconds: 5
                  ttl-seconds: 15
                backend:
                  enabled: true
                  unassigned-name: "unassigned"
                command:
                  primary: "vstats"
                  snapshot-cache-millis: 1000
                  player-list-limit: 100
                permissions:
                  view: "vstats.view"
                  staff: "vstats.staff"
                  list: "vstats.list"
                  reload: "vstats.reload"
                """.formatted(redisPort));
    }
}
