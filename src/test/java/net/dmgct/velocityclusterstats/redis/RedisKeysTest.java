package net.dmgct.velocityclusterstats.redis;

import net.dmgct.velocityclusterstats.TestConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisKeysTest {
    @Test
    void buildsKeysFromConfiguredPrefix() {
        RedisKeys keys = new RedisKeys(TestConfigs.config());

        assertEquals("vstats:nodes", keys.nodes());
        assertEquals("vstats:nodes:prx01:meta", keys.meta("prx01"));
        assertEquals("vstats:nodes:prx01:players", keys.players("prx01"));
        assertEquals("vstats:nodes:prx01:player_servers", keys.playerServers("prx01"));
        assertEquals("vstats:nodes:prx01:backends", keys.backends("prx01"));
    }
}
