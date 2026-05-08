package net.dmgct.velocityclusterstats.model;

/**
 * Aggregated backend server count split by public and staff Velocity nodes.
 *
 * @param serverName Velocity registered server name
 * @param publicPlayers players connected through public nodes
 * @param staffPlayers players connected through staff nodes
 */
public record ServerGroupCount(String serverName, int publicPlayers, int staffPlayers) {
    /**
     * Returns the public and staff player total for this backend server.
     *
     * @return total players on this backend server
     */
    public int totalPlayers() {
        return publicPlayers + staffPlayers;
    }
}
