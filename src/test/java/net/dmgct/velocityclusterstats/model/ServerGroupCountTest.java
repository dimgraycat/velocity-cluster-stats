package net.dmgct.velocityclusterstats.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerGroupCountTest {
    @Test
    void totalPlayersAddsPublicAndStaff() {
        assertEquals(7, new ServerGroupCount("lobby", 5, 2).totalPlayers());
    }
}
