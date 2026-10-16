# RaindanceMC/rtp

- **`rtp-module`** — Rain's Flexible Modules — Random Teleport: Send a player somewhere random in their own world, inside a ring an owner sets. The warm-up, the movement cancelling, the search for solid ground and the teleport itself are RainsCore's Travel — the same code warps, homes and teleport requests use. What is here is the ring itself, the cooldown and which worlds this runs in at all.
- **`rtp-standalone`** — Rain's Random Teleport: Rain's Random Teleport, as a plugin of its own. Nothing here but a build file and a descriptor: the rtp module and the standard wrapper are shaded together, and that is the whole plugin.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `rtp-standalone/target/RainsRandomTeleport-1.4.1.jar` — also published as `de.raindancer.modules:rtp-standalone:1.4.1`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
