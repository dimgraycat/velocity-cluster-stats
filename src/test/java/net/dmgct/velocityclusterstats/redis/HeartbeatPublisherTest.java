package net.dmgct.velocityclusterstats.redis;

import com.velocitypowered.api.proxy.ProxyServer;
import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeartbeatPublisherTest {
    @Test
    void publishSwallowsRedisFailures() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        when(proxyServer.getAllPlayers()).thenReturn(List.of());

        try (RedisManager manager = new RedisManager(TestConfigs.config(), mock(Logger.class))) {
            new HeartbeatPublisher(proxyServer).publish(TestConfigs.config(), manager);

            assertFalse(manager.isAvailable());
        }
    }
}
