package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class StatsRepositoryTest {
    @Test
    void loadSnapshotReportsRedisUnavailable() {
        try (RedisManager manager = new RedisManager(TestConfigs.config(), mock(Logger.class))) {
            assertThrows(
                    StatsRepository.StatsUnavailableException.class,
                    () -> new StatsRepository().loadSnapshot(TestConfigs.config(), manager)
            );
            assertFalse(manager.isAvailable());
        }
    }
}
