# Shrd Launcher 1.6.1

Fixes the crash that stopped the game from starting at all, and makes installing a mod bring its
libraries with it.

## The game starts again

1.6.0 shipped a launcher, not a game runtime. The trimmed Java 25 it carries was built from the
launcher's own module list, so it was missing pieces Minecraft and Fabric Loader need:

- `jdk.zipfs` — Fabric opens every mod jar as a `jar:` filesystem. Without it the game died before
  the loading screen with `ProviderNotFoundException: Provider "jar" not found`.
- `java.sql` — libraries reach for `java.sql.Time` during mixin bootstrap.
- `java.compiler` — libraries reach for `javax.annotation.processing.Messager`.

`java.management`, `java.naming`, `java.rmi`, `java.scripting` and `jdk.charsets` are in the runtime
too, so the next library that wants one does not cause another crash.

Verified by launching the installed build: 63 mods loaded, user set, sound engine started, world
generated and saved — no exceptions except a harmless AppleSkin mixin warning about JEI not being
installed.

## Installing a mod installs what it needs

Pressing **Install** on a Modrinth project now resolves that version's required dependencies and
installs them too, recursively and once each:

```
Installing: uku's Armor HUD
  file: ukus-armor-hud-fabric-0.12.0+mc26.2.jar  49661 bytes
  file: ukulib-fabric-2.1.1+26.2.jar             456607 bytes
  dependencies installed=1
```

Optional and incompatible dependencies are left alone, a project is only visited once, and a
dependency that cannot be fetched is logged instead of losing the mod you asked for.

## The Mods page fits the window

The search box was a `BorderLayout` row with no height cap inside a `BoxLayout`, so it swallowed all
the spare height and pushed the results out of view. It now has a fixed height, the result list keeps
its own, and the page is checked at 1180×740 and at the window's 1000×640 minimum.

## Downloads

    Shrd Launcher-1.6.1.exe        installer: per-machine, into Program Files, Start Menu entry,
                                   an entry in Apps & features, uninstalls cleanly
    Shrd Launcher-1.6.1.msi        the same installer as an MSI, for silent or scripted installs
    shrd-launcher-1.6.1-windows-x64.zip    portable: unzip anywhere and run the .exe inside

Every download carries its own (now complete) trimmed Java 25.

## Updating

An installed launcher checks this release's manifest on startup:

https://github.com/Nightshrd-afk/shrd-client/releases/latest/download/shrd-launcher-latest.json

Instances, downloaded libraries, assets and logs live in `%USERPROFILE%\.shrd-launcher`, so updating
or uninstalling the launcher does not touch your worlds or the client's settings.

## A note for this machine

Windows Application Control refused to start the launcher stub that the MSI installs, because that
stub is not code-signed. The 1.6.1 install here was finished by putting the signed launcher from the
app image in its place; the portable download is the signed one, so a fresh install from the zip is
not affected.
