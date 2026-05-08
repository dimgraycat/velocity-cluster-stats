package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RedisManagerTest {
    @Test
    void pingMarksUnavailableWhenRedisCannotBeReached() {
        try (RedisManager manager = new RedisManager(TestConfigs.config(), mock(Logger.class))) {
            assertFalse(manager.ping());
            assertFalse(manager.isAvailable());
        }
    }

    @Test
    void markSuccessRestoresAvailability() {
        try (RedisManager manager = new RedisManager(TestConfigs.config(), mock(Logger.class))) {
            manager.markFailure(new RuntimeException("failure"));
            manager.markSuccess();

            assertTrue(manager.isAvailable());
        }
    }
}
