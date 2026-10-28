# Shrd Launcher 1.6.0

Mods from Modrinth, a Home page arranged the way it should be, and a launcher that finds its own Java.

## Modrinth, inside the Mods page

The Mods page now opens on a browser for Modrinth. Search, sort and filter by game version, mod
loader and category, then press **Install** — the file lands in the active instance's own folder.

- **Mods** go to `mods/`, **Packs** to `resourcepacks/`, **Shaders** to `shaderpacks/`.
- Filters start on the instance's version and loader, so the list is what will actually load.
- Paging, result counts, downloads/followers/updated dates, and real project icons.
- The Installed tab is one click away, with the switches and Remove buttons it already had.

No API key is involved; Modrinth's v2 read endpoints are public. Modpacks are not supported yet —
only single projects.

## Java is found, and downloaded if it is missing

The installed copy could report "no Java found" on a machine that already had everything it needed,
because it only looked in a development toolchain folder. It now asks, in order: the runtime it is
running on, a `runtime/` or `jre/` beside the app image, a Minecraft runtime, its own downloaded
runtime, the usual JDK install folders, `JAVA_HOME`, then `PATH`.

If nothing on the machine can run Minecraft 26.2, the launcher downloads a plain Temurin JRE 25 from
Adoptium into `~/.shrd-launcher/runtime/jre-25` — no installer, no admin rights. That happens after
you sign in with Microsoft, when you press Play with no usable Java, or from Settings → Java →
**Find it for me**.

## Home

- No page title on Home: brand tabs take its place, the way the launcher's neighbours do it.
- The update offer moved to the top-right of the row above the banner.
- Your name plate sits over your character's head, and the banner is taller.
- The two actions at the bottom are an even split.
- The rail keeps its icon-only tiles and now ends with just your account's face.

## The skin

The player figure is turned slightly more towards you, lit a little higher, and its limbs have
stronger seams, so arms and legs read as four shapes instead of one block. The account chip is a
circle again rather than a squashed ellipse. The drawing was checked against Mojang's own render of
the same skin.

## Downloads

    Shrd Launcher-1.6.0.exe        installer: per-machine, into Program Files, Start Menu entry,
                                   an entry in Apps & features, uninstalls cleanly
    Shrd Launcher-1.6.0.msi        the same installer as an MSI, for silent or scripted installs
    shrd-launcher-1.6.0-windows-x64.zip    portable: unzip anywhere and run the .exe inside

Every download carries its own trimmed Java 25, so Java does not have to be installed.

## Updating

An installed launcher checks this release's manifest on startup and offers the new version:

https://github.com/Nightshrd-afk/shrd-client/releases/latest/download/shrd-launcher-latest.json

Instances, downloaded libraries, assets and logs live in `%USERPROFILE%\.shrd-launcher`, so updating
or uninstalling the launcher does not touch your worlds or the client's settings.

## Accounts

Sign in with the Microsoft account that owns Java Edition to play on servers that verify you — Donut
SMP and most public servers. Offline accounts still work for singleplayer and LAN.
