# Lectern Mod Loader

Loads [Lectern](https://lode.gg/mod/lectern) and keeps it up to date.

The jar a player installs is a container: this loader, plus a copy of the mod embedded inside it.
On every launch the loader checks for a newer version, downloads it beside the installed file
rather than over it, and registers whichever copy is newest with fabric-loader before the game
starts. An update therefore arrives on the launch it was found on, not the one after, and the file
the launcher installed is never modified.

Works offline: with no network, or with updates switched off, the embedded copy runs.

## Settings

`.minecraft/lectern/loader.properties`:

```properties
# Set to false to always run the embedded copy and never check for updates.
autoUpdate=true
```

`.minecraft/lectern/loader.log` records what happened on the last launch: what was unpacked, what
was skipped and why, and what was registered.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Nothing here needs Minecraft or Loom: the loader talks to
fabric-loader and Mixin, and everything it loads it loads by reflection.

## Licence

GPL-3, copyright (c) 2026 Lodestone Services LLC. See `LICENSE`.

This is a derivative of [Essential Loader](https://github.com/EssentialGG/EssentialLoader),
copyright ModCore Inc. d/b/a Essential, used under GPL-3. See `NOTICE`. Not affiliated with,
endorsed by, or sponsored by Essential.
