package net.dmgct.velocityclusterstats.redis;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartbeatPublisherTest {
    @Test
    void publishSwallowsRedisFailures() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        when(proxyServer.getAllPlayers()).thenReturn(List.of());
        when(proxyServer.getAllServers()).thenReturn(List.of());

        try (RedisManager manager = new RedisManager(TestConfigs.config(), mock(Logger.class))) {
            new HeartbeatPublisher(proxyServer).publish(TestConfigs.config(), manager);

            assertFalse(manager.isAvailable());
        }
    }

    @Test
    void publishWritesRegisteredBackendServersWithZeroPlayers() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        RegisteredServer lobby = server("lobby");
        RegisteredServer main = server("main");
        when(proxyServer.getAllPlayers()).thenReturn(List.of());
        when(proxyServer.getAllServers()).thenReturn(List.of(main, lobby));

        Jedis jedis = mock(Jedis.class);
        RedisManager manager = mock(RedisManager.class);
        when(manager.getResource()).thenReturn(jedis);

        new HeartbeatPublisher(proxyServer).publish(TestConfigs.config(), manager);

        verify(jedis).hset("vstats:nodes:prx01:backends", Map.of("lobby", "0", "main", "0"));
        verify(manager).markSuccess();
    }

    @Test
    void publishDoesNotWriteBackendsWhenBackendDisabledAndNoPlayersExist() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        RegisteredServer lobby = server("lobby");
        when(proxyServer.getAllPlayers()).thenReturn(List.of());
        when(proxyServer.getAllServers()).thenReturn(List.of(lobby));

        Jedis jedis = mock(Jedis.class);
        RedisManager manager = mock(RedisManager.class);
        when(manager.getResource()).thenReturn(jedis);

        new HeartbeatPublisher(proxyServer).publish(TestConfigs.configWithBackendEnabled(false), manager);

        verify(jedis).del("vstats:nodes:prx01:backends");
        verify(jedis, org.mockito.Mockito.never()).hset(eq("vstats:nodes:prx01:backends"), anyMap());
    }

    @Test
    void publishDoesNotCountUnassignedBackendWhenBackendDisabledAndPlayersExist() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        RegisteredServer lobby = server("lobby");
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("alpha");
        when(proxyServer.getAllPlayers()).thenReturn(List.of(player));
        when(proxyServer.getAllServers()).thenReturn(List.of(lobby));

        Jedis jedis = mock(Jedis.class);
        RedisManager manager = mock(RedisManager.class);
        when(manager.getResource()).thenReturn(jedis);

        new HeartbeatPublisher(proxyServer).publish(TestConfigs.configWithBackendEnabled(false), manager);

        verify(jedis).sadd("vstats:nodes:prx01:players", "alpha");
        verify(jedis).del("vstats:nodes:prx01:player_servers");
        verify(jedis).del("vstats:nodes:prx01:backends");
        verify(jedis, never()).hset(eq("vstats:nodes:prx01:player_servers"), anyMap());
        verify(jedis, never()).hset(eq("vstats:nodes:prx01:backends"), anyMap());
        verify(player, never()).getCurrentServer();
    }

    private RegisteredServer server(String name) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved("127.0.0.1", 25565)));
        return server;
    }
}
