# Velocity Cluster Stats

Velocity Cluster Stats is a Velocity proxy plugin that aggregates player counts across multiple Velocity nodes through Redis.

It is designed for clusters where several Velocity proxies connect players to one or more Fabric backend servers. No Fabric-side plugin is required.

日本語版: [README-ja.md](README-ja.md)

## Requirements

- Velocity `3.5.0-SNAPSHOT`
- Java 21
- Redis
- Gradle Wrapper for local builds

## Features

- Cluster-wide player count from multiple Velocity proxies
- Public proxy node count and player count
- Optional staff-only proxy node support
- Public-only clusters work without staff nodes
- Per-Velocity public node player counts
- Player list for all visible players, a specific node, or the staff group
- Backend server player counts using Velocity registered server names
- Registered backend servers are shown even when they currently have 0 players
- Colored chat output for headings, public/staff counts, values, and errors
- `/vstats reload` for config reload without restarting Velocity
- Redis failure-safe behavior: Velocity login, chat, and backend movement are not blocked when Redis is unavailable

## Commands

| Command | Description | Permission |
|---|---|---|
| `/vstats` | Show cluster summary | `vstats.view` |
| `/vstats public` | Show public node stats only | `vstats.view` |
| `/vstats staff` | Show staff stats only | `vstats.staff` |
| `/vstats servers` | Show backend server counts only | `vstats.view` |
| `/vstats list` | Show player list | `vstats.list` |
| `/vstats list public` | Show players on public nodes | `vstats.list` |
| `/vstats list <nodeId>` | Show players on a Velocity node | `vstats.list` |
| `/vstats list staff` | Show players on staff nodes | `vstats.list` + `vstats.staff` |
| `/vstats reload` | Reload config and Redis settings | `vstats.reload` |
| `/vstats help` | Show available vstats commands | none |

Console execution is treated as an administrator operation. Player permissions are delegated to Velocity's permission API.

## Permissions

This plugin does not depend on LuckPerms directly. It calls Velocity's permission API, so any Velocity-compatible permission plugin can provide these nodes.
If a permission is undefined, the command is allowed by default. Set a node to `false` in LuckPerms when you want to deny it.

| Permission | Allows |
|---|---|
| `vstats.view` | `/vstats`, `/vstats public`, `/vstats servers` |
| `vstats.staff` | `/vstats staff`, `/vstats list staff`, staff node player lists |
| `vstats.list` | `/vstats list`, `/vstats list public`, `/vstats list <nodeId>` |
| `vstats.reload` | `/vstats reload` |

Grant `vstats.*` to allow all Velocity Cluster Stats commands.
`/vstats help` has no permission node and shows only commands the sender can run.

LuckPerms examples:

```text
/lpv group admin permission set vstats.* true

/lpv group admin permission set vstats.view true
/lpv group admin permission set vstats.staff true
/lpv group admin permission set vstats.list true
/lpv group admin permission set vstats.reload true

/lpv group moderator permission set vstats.view true
/lpv group moderator permission set vstats.list true

/lpv group default permission set vstats.view true
```

The `vstats.*` line is enough for full access. The individual permission lines are useful when you want more granular control.

When `vstats.staff` is explicitly denied, `/vstats list` shows only public players. Staff-only stats and staff player lists are hidden.

## Installation

1. Build or download `VelocityClusterStats-<version>.jar`.
2. Place the jar in each Velocity proxy's `plugins/` directory.
3. Start Velocity once to generate:

   ```text
   plugins/velocity-cluster-stats/config.yml
   ```

4. Edit `config.yml` on each proxy node.
5. Restart Velocity or run `/vstats reload`.

Do not install this plugin on Fabric backend servers.

## Configuration

Each Velocity node must have a unique `node.id`.

```yaml
node:
  id: "prx01"
  group: "public"
```

Allowed groups:

- `public`: normal proxy nodes
- `staff`: staff-dedicated proxy nodes

Staff nodes are optional. If no active staff node exists, `/vstats` omits the `[Staff]` block and does not show `Staff 0` in server rows.

Redis settings are shared by all Velocity nodes:

```yaml
redis:
  host: "127.0.0.1"
  port: 6379
  password: ""
  database: 0
  key-prefix: "vstats"
  connection-timeout-millis: 1000
  socket-timeout-millis: 1000
  failure-log-cooldown-seconds: 60
```

Keep Redis timeouts short. Redis is used only for stats storage; Redis failures should not affect normal Velocity behavior.

## Build

Use JDK 21. If multiple JDKs are installed, set `JAVA_HOME` to your local JDK 21 installation before running Gradle.

```sh
./gradlew build
```

The deployable jar is:

```text
build/libs/VelocityClusterStats-<version>.jar
```

The `-thin.jar` artifact does not include runtime dependencies and is not the normal deployment jar.

## Tests

```sh
./gradlew test
```

## Release

GitHub Actions publishes a release only for tags beginning with `v`.

Example:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The workflow runs tests, builds the plugin, excludes the thin jar, and uploads `VelocityClusterStats-<version>.jar` to the GitHub Release.
