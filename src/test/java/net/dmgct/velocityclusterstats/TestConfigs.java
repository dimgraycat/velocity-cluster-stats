package net.dmgct.velocityclusterstats;

import net.dmgct.velocityclusterstats.config.PluginConfig;

public final class TestConfigs {
    private TestConfigs() {
    }

    public static PluginConfig config() {
        return configWithBackendEnabled(true);
    }

    public static PluginConfig configWithBackendEnabled(boolean backendEnabled) {
        return new PluginConfig(
                new PluginConfig.RedisConfig("127.0.0.1", 1, "", 0, "vstats", 10, 10, 0),
                new PluginConfig.NodeConfig("prx01", PluginConfig.GROUP_PUBLIC),
                new PluginConfig.HeartbeatConfig(5, 15),
                new PluginConfig.BackendConfig(backendEnabled, "unassigned"),
                new PluginConfig.CommandConfig("vstats", 1000, 100),
                new PluginConfig.PermissionsConfig("vstats.view", "vstats.staff", "vstats.list", "vstats.reload")
        );
    }
}
