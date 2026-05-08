package net.dmgct.velocityclusterstats.command;

import net.dmgct.velocityclusterstats.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReloadServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadSucceedsWithValidConfigEvenWhenRedisIsUnavailable() throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        loader.ensureDefaultConfig();

        ReloadService.ReloadResult result = new ReloadService(loader, mock(Logger.class)).load();

        assertTrue(result.success());
        assertFalse(result.redisAvailable());
        assertEquals("[vstats] Reload completed, but Redis is not available.", result.message());
        result.redisManager().close();
    }

    @Test
    void loadFailsWithInvalidConfig() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), "node:\n  group: invalid\n");

        ReloadService.ReloadResult result = new ReloadService(new ConfigLoader(tempDir), mock(Logger.class)).load();

        assertFalse(result.success());
        assertEquals("[vstats] Reload failed. Check console logs.", result.message());
    }
}
