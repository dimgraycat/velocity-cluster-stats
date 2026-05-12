# AGENTS.md

## Project

This repository contains a Velocity plugin project for Velocity `3.5.0-SNAPSHOT`.

Use the design document in `docs/` as implementation guidance, but do not implement broad feature work unless the user explicitly asks for it.

## Build

Use Gradle Wrapper:

```sh
./gradlew build
```

The project targets Java 21 bytecode. Run Gradle with JDK 21. If multiple JDKs are installed, set `JAVA_HOME` to a local JDK 21 installation before running Gradle. Running Gradle with newer unsupported JDKs may fail before the build starts.

## Dependency Rules

- Keep the Velocity API dependency on `com.velocitypowered:velocity-api:3.5.0-SNAPSHOT`.
- Keep Velocity API as `compileOnly` and `annotationProcessor`.
- Use the PaperMC Maven repository for Velocity dependencies.
- Do not bundle Velocity API into the final plugin jar.

## Implementation Scope

- This is a Velocity plugin only. Do not add Fabric-side plugin code unless requested.
- Keep initial setup changes separate from feature implementation.
- Do not add commands beyond the documented `/vstats` command set unless requested.
- Treat Redis as optional/degraded at runtime; plugin startup must not fail just because Redis is unavailable.

## Editing Guidelines

- Prefer small, focused changes.
- Do not rewrite files in `docs/` casually. If the user changes the specification through an instruction, update `docs/velocity_cluster_stats_design.md` so it stays aligned with the requested behavior.
- When creating or changing production code, always create or update the corresponding tests in the same change. Every public class should have a matching test class unless there is a documented reason it cannot be unit-tested.
- Do not revert unrelated user changes.
- Use ASCII unless the edited file already requires non-ASCII.

## Verification

For build verification, run:

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon build
```

Ensure this command runs with JDK 21. If needed, set `JAVA_HOME` to a local JDK 21 installation.

If dependency downloads are blocked by the sandbox, rerun the same command with approval for network access.
