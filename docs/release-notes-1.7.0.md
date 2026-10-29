# Shrd Launcher 1.7.0

The Mods page fits the window at every size the launcher allows, and a dependency pinned to one
specific version is installed as that version.

## The Mods page no longer runs off the right edge

A Swing label reports the width of its whole string as its preferred width, and Swing does not clip
a child to its parent, so one long Modrinth description made a result row wider than the page. The
description was cut mid-word *and* drawn straight over the Install button, and at the minimum window
size the result count drew underneath the sort menu.

- `Ui.Elided` is a one-line label that reports a small preferred width and draws its own ellipsis
  measured with `FontMetrics`, so a row can never grow past its container again. The project name,
  description, download numbers and installed jar names all use it.
- The result count moved into the card header, where there is always room for it, and the filters
  card scrolls on its own axis.
- Result rows are 84 px instead of 96, so more of the list is visible; installed-mod rows elide
  before the switch and the Remove button.

Checked by rendering the real window at 1000×640 (the smallest size the launcher allows), 1100×680,
1280×720 and 1512×876, on both the Browse and Installed tabs: no overlap, no clipped controls.

## Dependencies follow the version the mod was built against

Modrinth dependency entries can name an exact `version_id` rather than just a project. That version
is now installed instead of whatever the library's newest release happens to be. Against the live
API:

```
uku's Armor HUD -> ukus-armor-hud-fabric-0.12.0+mc26.2.jar + ukulib-fabric-2.1.1+26.2.jar
Iris Shaders    -> iris-fabric-1.11.4+mc26.2.jar         + sodium-fabric-0.9.2+mc26.2.jar
```

## Downloads

```
Shrd Launcher-1.7.0.exe                installer: per-machine, into Program Files, Start Menu entry,
                                       entry in Apps & features
Shrd Launcher-1.7.0.msi                the same installer as an MSI, for silent or scripted installs
shrd-launcher-1.7.0-windows-x64.zip    portable: unzip anywhere and run the .exe inside
```

Every download carries its own trimmed Java 25, so Java does not have to be installed.

## Updating

An installed launcher checks this release's manifest on startup and offers the new version:

https://github.com/Nightshrd-afk/shrd-client/releases/latest/download/shrd-launcher-latest.json

Instances, downloaded libraries, assets and logs live in `%USERPROFILE%\.shrd-launcher`, so updating
or uninstalling the launcher does not touch your worlds or the client's settings.
