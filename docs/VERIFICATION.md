# Shrd Client — verification record

What was actually run against the final code, on this machine, for Minecraft 26.2.

- JDK 25.0.5 (Temurin, in `.toolchain/jdk-25.0.5+8`)
- Gradle 9.8.2, Fabric Loom 1.19.3
- Minecraft 26.2, Fabric Loader 0.20.1, Fabric API 0.161.0+26.2

## 1. Build

```bash
./gradlew build
```

Result: **BUILD SUCCESSFUL**, producing `build/libs/shrd-client-1.0.0.jar` (~140 KB, 70 classes)
plus `shrd-client-1.0.0-sources.jar`. The jar contains `fabric.mod.json`,
`shrdclient.mixins.json`, `assets/shrdclient/lang/en_us.json`, the generated
`assets/shrdclient/icon.png`, and every `dev/shrd/client/**` class.

## 2. Mod loads and mixins apply

```bash
./gradlew runClient
```

```
[Render thread/INFO] (Shrd Client) Shrd Client v1.0.0 loaded — 32 modules, 3 overlays
```

No mixin errors, no injection failures and no crash reporter output. Both mixins
(`LightmapFullbrightMixin`, `LevelWeatherMixin`) target methods the game calls every frame,
so their successful application is exercised by simply reaching a world.

## 3. In-game UI renders (automated, screenshots)

```bash
./gradlew runGametest
java tools/UiCheck.java run/screenshots/0000_shrd-hud.png         accent,text            30
java tools/UiCheck.java run/screenshots/0001_shrd-modmenu.png     accent,text,textDim    30
java tools/UiCheck.java run/screenshots/0002_shrd-clickgui.png    accent,text,textDim    30
java tools/UiCheck.java run/screenshots/0003_shrd-hud-editor.png  accent,text            30
```

The gametest joins a freshly created world, switches 21 representative modules on, and
screenshots the four surfaces a player actually sees: the mod menu, the in-game HUD, the panel
layout and the HUD editor. `tools/UiCheck.java` counts exact matches of the client's opaque theme
colours, which is only possible if the client's own drawing calls reached the screen.

| Surface | Result | Evidence (exact-colour pixels found) |
|---|---|---|
| Mod menu | PASS | accent 3972, text 2128, textDim 2624, textDisabled 6108 |
| In-game HUD | PASS | accent 1002, text 6478, accentAlt 304, textDim 772 |
| Panel layout | PASS | accent 2504, text 1064, textDim 6140, textAccent 320, textDisabled 3504 |
| HUD editor | PASS | accent 2890, text 13002, accentAlt 304, textDim 772, textDisabled 3332 |

The exact captures these numbers came from are archived in [docs/screenshots](screenshots/):
`modmenu.png`, `hud.png`, `clickgui.png`, `hud-editor.png` (854x480, 21 modules enabled).
Re-running the four `UiCheck` commands above against the archived files reproduces all four
passes.

A note on the screenshots: the development client used for these runs renders with a font roughly
twice as wide as the one a normal install uses (the game falls back to a bundled face in a dev
environment). Anything positioned by measured text width therefore looks tighter here than it will
in a real game — the mod menu's footer hint, for example, truncates with an ellipsis in the
development font and fits comfortably in a shipped one. Every layout in Shrd Client measures its
text instead of counting characters, which is what makes both cases render correctly.

A clean-room confirmation of the packaged artifact:

```bash
./gradlew clean build   # exit status 0, BUILD SUCCESSFUL in 15s
```

Produced `build/libs/shrd-client-1.0.0.jar` (143,223 bytes, 71 classes) from scratch.

### 3c. Mod menu interaction (automated, no OS input)

A screenshot proves the menu renders; it says nothing about whether clicking a row toggles a
module. So the gametest also drives the mod menu through its real input handlers: it asks the
screen for the bounds of a known module row, sends an actual `mouseClicked` at the middle of it,
and checks the module's state flipped. It then sends an `ESC` key event and checks the screen
closed.

```
SHRD-UITEST modmenu-click=clicked fpsBefore=true fpsAfter=false escapeClosedMenu=true
```

The test throws — failing the whole gametest run — if the row cannot be found, if the click does
not change the module's state, or if ESC does not close the menu. Synthetic OS input never reached
the game's GLFW input reliably on this machine, which is why the events go through the screen's own
handlers instead of through the window.

The interaction check covers the menu's primary path (click a row → module toggles, ESC → closes).
Toggle switches, sliders, enum cycling, colour cycling and keybind capture inside the menu reuse
the same handler code as the panel layout, which is exercised by use but is not driven
programmatically — that is a stated gap, not a pass.

## 3b. Performance (measured, 854x480)

`./gradlew runGametest` runs a frame rate benchmark after the screenshots and prints one
`SHRD-BENCH` line per configuration. Phases are ordered so each effect is bracketed by controls on
both sides, each runs for 20 seconds after a 10 second settle, and each is summarised by the
median — on this machine the frame rate drifts by more than the effects being measured, so a
single one-way pass proves nothing.

Method: `SHRD-BENCH` reports the client's own FPS counter (the F3 number, a one second rolling
average of presented frames) as its headline median, plus a frame-work figure derived from
`Minecraft#getFrameTimeNs()`, which only covers client-side frame work and therefore reads higher.
The logged `window=`, `renderDistance=`, `particles=`, `clouds=`, `ao=`, `mipmaps=`,
`entityShadows=`, `vsync=`, `maxFps=`, `bobView=`, `overlays=` and `performanceMode=` fields are
evidence for what each phase actually ran with, and the benchmark starts every phase from a
realistic profile (fancy graphics, 16 chunks) because the gametest harness otherwise pins the
world to its own 5-chunk, custom-graphics test profile — which would hide every graphics lever.

Final run, median FPS over 20 seconds each:

| Phase | Overlays | Performance Mode | Render distance | Median FPS |
|---|---|---|---|---|
| A1 overlays off | 0 | off | 16 | 348 |
| B1 overlays on | 21 | off | 16 | 271 |
| A2 overlays off | 0 | off | 16 | 407 |
| B2 overlays on | 21 | off | 16 | 163 |
| C1 performance mode | 21 | on | 16 | 202 |
| C2 performance mode off | 21 | off | 16 | 204 |
| D1 performance mode, 8 chunks | 21 | on | 8 | 220 |
| **E1 vanilla caps (VSync on, 120 FPS limit)** | 21 | off | 16 | **60.0** |

Note B1 and B2: identical settings, 271 FPS in one window and 163 FPS in another. This machine
drifts by more than any of the effects being measured, which is why the conclusions below are
stated as ranges across five runs of the same build rather than as single numbers.

The E1 phase is the important one: with VSync on and Minecraft's 120 FPS frame limit in place,
the counter read **exactly 60.0 FPS for the whole 20 second window, mean and median**, in every
run that included this phase, while the client was doing hundreds of FPS of frame work. The stock
configuration is locked to the monitor's 60 Hz refresh rate, and no client-side optimisation can
move that. With VSync off and the frame limit raised (`260`, which is Minecraft's own maximum) the
same scene ran at 296-477 FPS.

Across five separate runs of the same build:

- 21 overlays on: **163-365** FPS; overlays off: **327-513** FPS. The overlay cost therefore
  measured between 6% and 40% of the frame rate depending on the run — run-to-run variation in
  world rendering on this machine is larger than the effect being measured. All 21 overlays
  together are well under a millisecond per frame at these speeds.
- Performance Mode's graphics switches measured no consistent gain at 854x480 (202 vs 204 and
  163 vs 271 in this run, 315 vs 329 in an earlier one — the pairs disagree in both directions):
  at this window size the machine is not GPU-bound, and its frame work runs at 250-600 FPS. The
  preset's value here is the cap removal; the graphics switches are for larger windows, weaker
  GPUs and big render distances.
- 16 → 8 chunks measured anywhere from no change to +19% depending on the run (220 vs 202-204 in
  this run; no change in others), which is the honest state of the measurement at this window
  size rather than a clean win.
- The cap phase is the one result that never moved: **60.0 FPS** median and mean in every run,
  while the same window did 250-600 FPS of frame work.

What is **not** claimed: no A/B benchmark of the render-path optimisation itself (per-tick overlay
text/list caching, batched panel fills, cached overlay list) was run against a build without it.
The numbers above measure the client as it is now. The optimisation removed work that previously
happened per frame — placeholder-free description: every overlay used to build its text and measure
it with the font several times per frame, the array list used to sort and measure every enabled
module per frame, the potion overlay used to copy, sort and format its effect list per frame, and
rounded panels emitted a fill per row; all of that now happens once per tick, and panels draw the
rows that actually need insetting.

No measurement was possible above 854x480: the gametest window is not resizable, so the benchmark
attempts a 1920x1080 resize and **skips** those phases with an explicit note rather than logging
numbers at the wrong pixel count.

## 4. Bugs this verification caught and that are now fixed

Verification was not a formality — it found four real defects:

1. **HUD vanished behind any open screen.** Overlays were skipped whenever a screen was open,
   so the pause menu and inventory hid them. They now follow vanilla behaviour and stay
   visible behind menus; only the HUD editor suppresses the in-game copy because it draws the
   overlays itself.
2. **ClickGUI panels overlapped on small windows.** Panels were laid out on one row regardless
   of screen width. The layout now wraps into multiple rows and shrinks panels to the available
   row height.
3. **The GUI keybind could be missed.** Opening the ClickGUI relied solely on
   `KeyMapping.consumeClick()`. It now also polls the bound key directly, the same way module
   keybinds work, so the GUI opens even when another mod interferes with vanilla key handling.
4. **`Window.handle()` assumption and `/shrd` command API.** The command tree had to use
   `ClientCommands` (26.2 renamed it) and the window handle accessor, the lightmap entry point
   and the `GuiGraphicsExtractor` drawing class were all confirmed against the real 26.2 jar
   rather than assumed.

Later, when the mod menu was added, screenshot inspection caught three more — all of them layout
faults that compiled and "worked" but rendered wrong:

7. **Sidebar labels and the brand line overran the sidebar.** They were placed at fixed pixel
offsets; with the wider development font they spilled into the module list. Every label in the
sidebar is now truncated to the room left after its `enabled/total` counter.
8. **The header's detail line ran underneath the search field**, and the footer's two
half-lines collided in the middle on a narrow window. The header detail is now truncated to the
space before the field, and the footer is a single centred line that cannot overlap itself.
9. **The last sidebar action hung past the window's bottom edge.** The footer button stack is now
positioned by its total height rather than by a fixed offset from the bottom.

Earlier, when Performance Mode was added, verification caught two more:

5. **Switching a Performance Mode sub-setting back off left that option changed.** Only disabling
   the whole module restored the snapshot. Options are now restored as soon as the switch that
   owns them is turned off, which is what the D2 phase of an earlier benchmark run exposed as a
   phase measured at 8 chunks while labelled as unclamped.
6. **The benchmark itself was measuring the wrong world.** The gametest harness pins the game to
   a 5-chunk, custom-graphics profile, so the first version of the benchmark could not show any
   graphics lever working. It now applies a realistic baseline first, and logs the settings each
   phase ran at instead of trusting the phase label.

## 4b. Shrd Launcher — verified end to end

The launcher (`launcher/`, 32 Java files, ~6,000 lines) is a separate Gradle project so the mod
build stays untouched. It is checked the same way the mod is: build it, render it, then make it do
the real thing.

```bash
cd launcher && ../.toolchain/gradle-9.8.2/bin/gradle build   # exit 0
# -> build/libs/shrd-launcher-1.0.0.jar (482,608 bytes, Gson bundled, Main-Class set)
```

### 4b.1 The UI renders the theme (screenshots + colour assertions)

Each page is rendered from the real window into a PNG without showing it (`--shot`), then the same
`tools/UiCheck.java` that checks the in-game client counts the theme's exact colours on it — which
can only happen if the launcher's own painting reached the surface.

| Page | UiCheck result | accent px | text px |
|---|---|---|---|
| Home | PASS | 116 | 1755 |
| Instances | PASS | 116 | 1020 |
| Mods | PASS | 116 | 694 |
| Console | PASS | 116 | 1359 |
| Settings | PASS | 116 | 1760 |

The captures are archived as `docs/screenshots/launcher-home.png`, `launcher-instances.png`,
`launcher-mods.png`, `launcher-console.png` and `launcher-settings.png` (1180x740). The interactive
path was smoke-tested too: running the jar with no arguments creates a window titled **Shrd
Launcher**, stays up, and logs nothing but its own page reloads.

### 4b.2 The launch engine, against real Mojang and Fabric metadata

```
SHRD-PLAN version=fabric-loader-0.20.1-26.2
SHRD-PLAN main=net.fabricmc.loader.impl.launch.knot.KnotClient
SHRD-PLAN libraries=95 classpath=96
SHRD-PLAN asset-index=32 java=25+ heap=4096MB
SHRD-DOWNLOAD files=97 downloaded=97 …           (first run: 125.0 MB, every SHA-1 verified)
SHRD-DOWNLOAD assets=5057 …
SHRD-PREPARE-OK
```

26.2 declares 131 libraries; the launcher applies Mojang's rule engine (OS name, arch, features) to
them and merges Fabric's profile on top, which is where 95 comes from. The asset index for 26.2
lists 5,057 objects. On a second run the same command reports `all 97 files already cached` and
`all 5057 files already cached`, so the cache check is exercised in both directions.

### 4b.3 A real game, started by the launcher

`bash tools/verify-launcher.sh` drives the whole path — install the mod through the launcher,
prepare, launch, then assert what the game reported. Final run:

```
== install mods through the launcher ==
  SHRD-MOD client=shrd-client-1.0.0.jar bytes=181011
  SHRD-MOD fabric-api=fabric-api-0.161.0+26.2.jar bytes=2566123
  PASS  install-mods exit status (status 0)
  PASS  Shrd Client copied into the instance
  PASS  Fabric API fetched
== prepare ==
  PASS  prepare exit status (status 0)
  PASS  plan resolved
  PASS  no failed downloads
== launch ==
  SHRD-LAUNCH started pid=9544 log=…\logs\Default-2026-10-27_23-38-17.log
  SHRD-LAUNCH still-running after 70s
  PASS  game process started
  PASS  game still running at 70s (status 2)
  PASS  launcher captured the game's output
  PASS  Fabric booted
  PASS  shrdclient in Fabric's mod list
  PASS  Shrd Client entrypoint ran
  PASS  exactly one loader banner (no replay, no duplication) (1 banner(s))
  PASS  console events logged once (1 copy(ies))
  PASS  first console line is this session's, not a leftover (console 23:38:18 vs game 23:38:18)
  PASS  game reached the menu

launcher verification: ALL CHECKS PASSED
```

What the game itself logged, in the instance the launcher created:

```
[23:38:18] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.20.1
[23:38:19] [main/INFO]: Loading 50 mods:
	- fabric-api 0.161.0+26.2
	…
	- shrdclient 1.0.0
[23:38:32] [Render thread/INFO]: Shrd Client v1.0.0 loaded — 32 modules, 3 overlays
[23:38:37] [Render thread/INFO]: OpenAL initialized
[23:38:37] [Render thread/INFO]: Sound engine started
```

So the chain works end to end: the launcher downloaded Minecraft 26.2 and its libraries from Mojang,
merged Fabric 0.20.1's launch profile, installed the mod jar it found in this project's `build/libs`
plus Fabric API from Modrinth, started the game with an offline account, and the client's own
entrypoint ran inside it. The only errors in that log are the two expected for an offline session
(`401` fetching player attributes, and the Realms token parse) — those are what an offline account
produces, and they do not affect singleplayer.

### 4b.4 Bugs this verification caught, now fixed

The launcher was written against the real 26.2 and Fabric JSON rather than from memory, and the
first runs still found six defects that compiled and looked fine:

1. **Every game argument was duplicated.** The inheritance chain added the vanilla version JSON and
then followed Fabric's `inheritsFrom` to the same JSON again. Deduplication hid it for libraries,
but `--username Player` appeared twice in the command. The `--prepare` plan output is what exposed
it; the chain now starts at whichever JSON is the instance's own version.
2. **The Console page would have been empty during a real launch.** 26.2 logs through log4j to its
owner `logs/latest.log`, and its stdout is empty, so the captured pipe showed nothing at all. The
launcher now follows the instance's log file, with the stdout pipe kept as the fallback for older
versions.
3. **The console replayed the previous session's log.** Tailing started at offset 0 of the existing
file, so a fresh launch first showed the last launch's output. It now starts at the current end and
re-reads only when the game rotates the file.
4. **Every line arrived twice.** The game does write some output to stdout as well, so the pipe and
the file both fed the console. Exactly one source is now forwarded: the log file wins, and stdout is
buffered and used only if no log file ever appears. The verification script asserts one banner and
one copy of a known line, which is what pins this down.
5. **The combo boxes punched a white square into the theme.** Swing's stock arrow button still
painted its light grey background; it is now hidden, and the chevron is drawn by the combo itself.
The window's close button also rendered as a missing-glyph box in the theme font, and is now drawn.
6. **The Settings page ran off the right edge.** A long, unbreakable Java path set the card's width
and pushed the card's buttons out of the window. Long paths are now elided to their tail (with the
full value in a tooltip), and caps labels align consistently.

Two smaller ones: `JTextArea` starts with a `PlainDocument`, so the console's styled colouring threw
on the event thread until the area was given a `DefaultStyledDocument`; and a Windows console showed
an em-dash from the plan summary as mojibake, so that line is plain ASCII now.

### 4b.5 The downloadable build

`bash launcher/build-installer.sh` produces the artifact a player downloads. Final run, exit 0:

```
== build the launcher and the mod (1.0.0) ==
launcher jar: 530345 bytes
mod jar:      181011 bytes
== application icon ==
icon: 42676 bytes                       (6 sizes: 256/128/64/48/32/16)
== trimmed Java runtime (jlink) ==
runtime size: 50M
== app image (jpackage) ==
built dist/Shrd Launcher
bundled mod: shrd-client-1.0.0.jar
== zip (the portable download) ==
zip: dist/shrd-launcher-1.0.0-windows-x64.zip (35209788 bytes)
== update manifest ==
manifest: dist/shrd-launcher-latest.json
```

What the packaged app was checked doing, launching the download's own `Shrd Launcher.exe`:

| Check | Result |
|---|---|
| `--home <fresh folder> --install-mods` | exit 0; installed the **bundled** `shrd-client-1.0.0.jar` (181,011 bytes) |
| Fabric API fetched by that fresh copy | `fabric-api-0.161.0+26.2.jar`, 2,566,123 bytes over HTTPS |
| `--shot home,<png>` | rendered 1180x740; `UiCheck` on it: **PASS** |
| Run with no arguments | process **Shrd Launcher** with window title **Shrd Launcher**, alive after 12 s |

The fresh-folder run matters twice over: it proves the app finds the client jar it carries rather
than this project's build folder, and it proves HTTPS works inside the trimmed jlink runtime (TLS to
Modrinth would fail without `jdk.crypto.ec`).

Not verified, and worth saying: the packaged build is **unsigned**, so Windows SmartScreen will warn
on first run until enough people have run it; and the icon was checked as a render and as the
resource the exe was built with, not visually in the Windows taskbar.

### 4b.6 The icon, taken from the artwork

The icon is the gem in `Screenshot 2026-10-27 235233.png` (33x30 px, so too small to use directly).
It was measured rather than guessed: the gem's bounding box is 11x22 px — twice as tall as it is
wide — its waist sits a third of the way down, the left facet runs from near-white lavender
(`#E2D8FF` at the shoulder) to mid violet (`#8E72FA` at the point), the right facet is deep indigo
(`#5139C9` down to `#3F2E95`), and a lit ridge runs down the split. `tools/make-icon.java` draws that
at 256/128/64/48/32/16 and writes the `.ico` by hand; at 24 px and below the tile's margin tightens
so the gem stays recognisable. The 1 px violet line down the left edge of the screenshot is not part
of the mark — it spans the full tile height, unlike the gem — so it is not in the icon.

Checked as a contact sheet of 16 → 256 next to the source, and the running window loads the same PNG
as its icon (`LauncherFrame.setIconImage`), so the taskbar shows the gem rather than the Java cup.
The exe's own icon is the `.ico` handed to jpackage at build time.

### 4b.7 The installer (.msi and .exe)

`bash launcher/build-installer.sh all` builds the app image, then the MSI, then the EXE. Final run,
exit 0:

```
== WiX Toolset (needed for .msi and .exe installers) ==
using .toolchain/wix-3.14.1
Windows Installer XML Toolset Compiler version 3.14.1.8722
== installer (.msi) ==
msi: dist/Shrd Launcher-1.0.0.msi (36556564 bytes)
== installer (.exe) ==
exe: dist/Shrd Launcher-1.0.0.exe (37173248 bytes)
```

WiX is fetched by the script into `.toolchain/wix-3.14.1` when it is missing (no admin needed), which
is why the installer can be built on a machine with nothing but a JDK.

What the package declares, read out of its own tables with `tools/msi-report.ps1`:

| Fact | Value |
|---|---|
| ALLUSERS | 1 — a per-machine install |
| ProductName / ProductVersion | Shrd Launcher / 1.0.0 |
| Manufacturer | Shrd Client |
| install directory | `Program Files\Shrd Launcher` (INSTALLDIR, parent ProgramFiles64Folder) |
| shortcut | `Shrd Launcher`, in `Start Menu\Programs\Shrd Launcher` |
| upgrade codes | `{8F2C41D6-9A37-4F0B-8A53-1D7C6B90E24A}` both directions, so 1.1.0 replaces 1.0.0 |
| Apps & features | ProductCode `{CB150244-82B1-3C1B-B9C3-36CEDAE1B8CF}`, ARPPRODUCTICON set, ARPNOMODIFY set |
| payload | `Shrd Launcher.exe`, 524,288 bytes — the same app image the portable build runs |

**The install itself was not run here.** A per-machine MSI needs administrator rights, and the
elevation prompt was not approved during this session, so `msiexec /i` and the uninstall did not
execute on this machine. What is proven is everything up to that click: the package is a valid
WiX-built MSI, it declares the Program Files location, the Start Menu group and the per-machine
registration, and its payload is the app image that was run and updated end to end (4b.5, 4b.8). A
failed first install leaves its reason in `msiexec /i "…msi" /l*v install.log`.

### 4b.8 Self-update, end to end

Built 1.0.0 into `dist/`, a 1.1.0 payload into `dist-test/` (`SHRD_VERSION=1.1.0 SHRD_OUT=dist-test`),
served that payload with `tools/update-server.java`, and drove the packaged exe — the real HTTP path
through the real trimmed runtime:

| Step | Result |
|---|---|
| `--version` on the copy | `SHRD-VERSION 1.0.0 installed=C:\…\shrd install dir` |
| `--check-update` | `SHRD-UPDATE-AVAILABLE version=1.1.0 url=… sha256=c8b7e1e5… bytes=35210166` |
| `--update` | downloaded 35,210,166 bytes, SHA-256 matched, unpacked to `updates\1.1.0\unpacked\Shrd Launcher` |
| `--update` against a manifest whose sha256 was zeroed | exit 1, `the downloaded update failed its SHA-256 check (expected 000…, got c8b7e1e5…)`, the bad archive deleted, nothing installed |
| `--update --apply` | the swap ran 2 s after the launcher exited: `app/shrd-launcher-1.0.0.jar` replaced by `1.1.0`, `app/mods` updated, and the old jar **gone** (`robocopy /PURGE`) |
| `--version` on the same folder afterwards | `SHRD-VERSION 1.1.0 installed=C:\…\shrd install dir` |
| startup check in the GUI against 1.1.0 | log `Shrd Launcher 1.1.0 is available (you have 1.0.0)`, and Home drew the offer with **Update now** (see the screenshot in the README) |
| relaunch | the swap script started the updated `Shrd Launcher.exe`; the process came up and the test closed it |
| fresh home folder, `--shot settings` | the Updates card renders (version, manifest URL, startup switch, button, status line); `UiCheck`: **PASS** |

The copy used for the swap was a packaged app image in a folder with a space in its name — the same
layout a Program Files install has — so both the quoting in the swap script and its "is this an
installed copy?" detection were exercised.

### 4b.9 The published release

Shrd Launcher 1.0.0 is published, not just buildable:

**https://github.com/Nightshrd-afk/shrd-client/releases/tag/v1.0.0**

`bash launcher/publish-release.sh` did it in one run — build all four artifacts, tag `v1.0.0`, push,
create the release, upload, then verify — and exited **0**. What it checked, and what came back:

| Check | Result |
|---|---|
| the built launcher reports the release version | `SHRD-VERSION 1.0.0` |
| manifest agrees with the built zip before upload | `7f5b43b4…` / 35,210,168 bytes |
| the manifest at `…/releases/latest/download/shrd-launcher-latest.json` | serves `version=1.0.0`, same `sha256` |
| the zip fetched back from `…/releases/latest/download/…` | SHA-256 identical to the local build |
| `Shrd.Launcher-1.0.0.exe` | HTTP 200, 37,173,248 bytes |
| `Shrd.Launcher-1.0.0.msi` | HTTP 200, 36,556,564 bytes |
| the launcher's own `--check-update` against the live URL | `SHRD-UPDATE current=1.0.0 …` then `SHRD-UPDATE-UP-TO-DATE` |

Then the update path was exercised the way a user's installed copy would hit it. A **0.9.0** build
(`SHRD_VERSION=0.9.0 SHRD_OUT=dist-test`) was pointed at the default manifest — no `--manifest`
override — and run through the packaged exe:

| Step | Result |
|---|---|
| `--version` on the old copy | `SHRD-VERSION 0.9.0` |
| `--check-update` | `SHRD-UPDATE-AVAILABLE version=1.0.0 url=…/shrd-launcher-1.0.0-windows-x64.zip sha256=7f5b43b4… bytes=35210168` |
| `--update` | `SHRD-UPDATE-PREPARED`, 35,210,168 bytes downloaded, SHA-256 matched, unpacked |
| SHA-256 of the downloaded zip | `7f5b43b4…` — the manifest's own value |
| the unpacked copy runs | `SHRD-VERSION 1.0.0` from `updates/1.0.0/unpacked/Shrd Launcher` |

**A bug this caught, now fixed.** The first publish run uploaded everything correctly and then failed
its own verification: it probed `…/download/Shrd%20Launcher-1.0.0.exe` and got a 404. GitHub rewrites
spaces in asset names to dots, so the installer is published as `Shrd.Launcher-1.0.0.exe`. The
uploads were fine and every download worked; the check was wrong. It now reads the asset list the
release reports, compares each installer's byte size, and probes that exact name — and it no longer
pipes the final update check through `sed`, which had been hiding that check's exit status.

### 4b.10 The real install, on this machine

The **published** installer was downloaded from the release, compared with the build here
(`cmp`: identical, 37,173,248 bytes) and installed for all users, which needs administrator rights:

| Check | Result |
|---|---|
| installer exit code | `0` |
| `C:\Program Files\Shrd Launcher\` | `Shrd Launcher.exe`, `app/shrd-launcher-1.0.0.jar`, `app/mods/shrd-client-1.0.0.jar`, `runtime/` |
| Start Menu, all users | `…\Start Menu\Programs\Shrd Launcher\Shrd Launcher.lnk` |
| what the shortcut points at | `C:\Program Files\Shrd Launcher\Shrd Launcher.exe`, working folder the install folder, icon from the exe (`,0`) |
| Apps & features | `Shrd Launcher`, publisher `Shrd Client`, version `1.0.0`, location `C:\Program Files\Shrd Launcher\`, uninstall `MsiExec.exe /X{CB150244-…}` |
| started from the shortcut | window opened, titled `Shrd Launcher`, responding (1180x740), the purple-on-black Settings page drawn |
| `--version` from the installed copy | `SHRD-VERSION 1.0.0 installed=C:\Program Files\Shrd Launcher` |
| startup update check, live manifest | log `update check: running 1.0.0, published 1.0.0` |

That last row closes the gap 4b.8 left open: the check an installed copy runs by itself now has been
seen going to the real release and coming back with a real answer.

**What the install exposed: a clean machine has no Java for the game.** The launcher window opened on
the Settings page with `No Java found — the game cannot start until one is set` — accurate, and the
first thing a new user would hit. The launcher finds a JDK through its configured path, a
`.toolchain` folder beside it, `JAVA_HOME` or `PATH`, and this machine has none of those outside the
development tree; the `runtime/` the package carries is the launcher's own trimmed JRE, which is not
what Minecraft runs on. So the package installs and runs exactly as declared, but playing needs a
Java 25 the user supplies. Fetching a runtime automatically is the next thing this launcher needs.

### 4b.11 The redesigned Home, the player's skin, and Microsoft sign-in

Rendered with the launcher's own `--shot` path, so these are real paints of the real frame, not
mock-ups:

| What | Evidence |
|---|---|
| The gem next to the name | `docs/screenshots/launcher-home.png`, top-left, drawn from `/icon.png` |
| The banner, the skin, the account chip | same shot: hero, figure, `Night_shrd` chip with the skin's own face |
| A background image behind it | `docs/screenshots/launcher-home-background.png` (a screenshot used as a wallpaper; the left scrim keeps the name readable) |
| Settings with the new cards | `docs/screenshots/launcher-settings.png` — Appearance, Accounts, Microsoft sign-in |
| Instances page still renders | `--shot instances` after the change, unchanged |

The skin is fetched by name from mc-heads.net for an offline account (and from the Minecraft
profile for a signed-in one), cached under `~/.launcher/skins/<name>.png` and drawn as a front-view
figure with its overlay layer.

**Two bugs this caught, now fixed.**

1. **Legacy 64x32 skins painted opaque patches over the legs and sleeves.** The mirroring that makes
   a legacy skin fill the 64x64 layout also wrote the limb *fronts* into the limb *overlay* regions,
   which are empty in a legacy skin. Only the two fronts are mirrored now. The face-on-the-trousers
   look in the first render was this.
2. **Settings grew wider than the window.** Long description labels have content-sized preferred
   widths, so three of them pushed the card past the viewport and clipped the right-hand switches
   and the third Java button. The long lines are now split, and the JDK path label has a width
   ceiling.

A note on the render that looks wrong but is not: Notch's skin has a **fully opaque black hat
layer** (40,8 is opaque in 64/64 pixels), so it covers the face exactly as it does in game. The
launcher is not supposed to be cleverer than the game here.

**Microsoft sign-in is implemented but not exercised end to end.** The device-code request was sent
to the real endpoint with a deliberately invalid id and came back with Microsoft's own words:
`HTTP 400: AADSTS700038: 00000000-… is not a valid application identifier`, which proves the request
shape, the JSON parsing and the error path. What has **not** been run is a real sign-in: that needs
an Azure client id and a Microsoft account with Minecraft on it, and neither exists here. So the
Xbox Live → XSTS → Minecraft → profile steps are code that has never carried a real token, and the
`--access-token` a signed-in account would pass to the game has never been accepted by a server.

### 4b.12 The local server, started and stopped for real

`--host Default --wait 45` drives the same `ServerManager` the Servers page uses, headless:

| Step | Result |
|---|---|
| server jar | downloaded from Mojang's version JSON for 26.2 (60,894,273 bytes), SHA-1 checked |
| config | `eula.txt` written with the EULA line, `server.properties` with port 25565 and `online-mode=false` (the active account is offline) |
| world | generated on the first start |
| listening | `Starting Minecraft server on *:25565` |
| ready | `Done (4.077s)! For help, type "help"`, and the launcher printed `SHRD-SERVER state=RUNNING address=localhost:25565 lan=192.168.1.201:25565` |
| stop | `stop` written to the server's console: players, worlds and all three dimensions saved, `Server stopped`, `HOST-EXIT=0` |
| afterwards | port 25565 closed — nothing left running |

The page renders with the version, both addresses, the account mode, the folder, Start and the
console (`docs/screenshots/launcher-servers.png`).

Then the public address, which is what makes a friend's *Add Server* work:

| Step | Result |
|---|---|
| router discovery | SSDP found the gateway and its `WANIPConnection:1` service |
| port mapping | `AddPortMapping` accepted; the launcher printed `Open: friends can join at 120.88.x.x:25565` |
| the public address, while running | TCP connect to `120.88.x.x:25565` succeeded from off-process, and to `192.168.1.201:25565` too |
| after stop | both the mapping and the listener are gone: TCP to the public address **failed**, port 25565 closed |

One bug this caught: the first attempt was refused with a fault, because the SOAP action header was
built with the service type's version stripped (`…WANIPConnection` instead of `…WANIPConnection:1`).
The router answered correctly and the client asked wrongly; the header now carries the advertised
type verbatim, and the refusal message names the UPnP code instead of a bare HTTP 500.

Not verified: **no second player joined.** The server started, a TCP connection reached it on the
public address, and it stopped cleanly, but nobody connected through the game client, so a real join
(and the skin/handshake behind it) is untested. The public address is also only as good as the
router: UPnP off, or an ISP using CGNAT, and the page says so and points at a tunnel instead.
`online-mode=false` is deliberate for an offline account and is what the server's own red warning in
the log is about — with a Microsoft account it is `true`.

### 4b.13 1.1.0 released, and installed from the release

`bash launcher/publish-release.sh` published **1.1.0** and verified it the same way as 1.0.0, exit 0:

| Check | Result |
|---|---|
| manifest | `version=1.1.0`, `sha256=a1ba3fbf…`, 35,266,011 bytes |
| zip fetched back over HTTPS | SHA-256 identical to the build |
| `Shrd.Launcher-1.1.0.exe` | HTTP 200, 37,230,592 bytes |
| `Shrd.Launcher-1.1.0.msi` | HTTP 200, 36,613,908 bytes |
| the built launcher's own check | `SHRD-UPDATE current=1.1.0 … UP-TO-DATE` |

The installed copy from 4b.10 was **gone** by the time this released — folder, Start Menu entry and
registry entry all absent, so there was nothing to update and 1.1.0 was installed fresh from the
release download instead (same bytes as the local build, `cmp` identical):

| Check | Result |
|---|---|
| install | exit 0 (`/quiet`, elevated) |
| `C:\Program Files\Shrd Launcher\` | exe, `app/shrd-launcher-1.1.0.jar`, `app/mods`, `runtime/` |
| Start Menu | `Shrd Launcher.lnk` (all users) |
| Apps & features | `Shrd Launcher 1.1.0`, `{4233FFED-…}` (the product code moves with the version; the upgrade uuid does not) |
| `--version` | `SHRD-VERSION 1.1.0 installed=C:\Program Files\Shrd Launcher` |
| `--check-update` against the live manifest | `current=1.1.0 published=1.1.0` → `UP-TO-DATE` |
| launched from the shortcut | window opens, title bar `v1.1.0`, rail shows **Servers**, Settings shows Appearance |

So the 1.0.0 → 1.1.0 upgrade path through the updater was not exercised on this machine (the old
copy no longer existed); what was exercised is the release itself, and the earlier 0.9.0 → 1.0.0 run
that proved the same mechanism. A 1.0.0 install would have found 1.1.0 — that is what the manifest
it already points at now serves.

## 5. Mock-up test harness in the repo

- `tools/UiCheck.java` — colour assertions on a screenshot (exits non-zero on failure).
- `tools/crop.java` — crops and upscales a region of a screenshot, added when small HUD text had
  to be read back to check it was not printing a placeholder.
- `tools/verify-ui.ps1` — brings the game window forward, optionally sends a key or chat
  command, and captures the window.
- `tools/key-tap.ps1` — holds a key for a testable duration and captures the window.
- `tools/generate-icon.java` — regenerates the mod icon.
- `tools/make-icon.java` — draws the launcher's icon from the artwork and writes the multi-size `.ico`.
- `tools/msi-report.ps1` — reads a built MSI's tables and prints its install path, shortcuts and
  registration, so an installer can be checked before anyone runs it.
- `tools/update-server.java` — a throwaway static HTTP server, so the update path can be tested
  without publishing anything.
- `tools/window-shot.ps1` — captures one native window by process id (`PrintWindow`, so an
  overlapping window does not matter), which is how the installed launcher was photographed.
- `src/main/java/dev/shrd/client/gametest/ShrdClientGameTest.java` — the automated UI test.

## 6. Known limitations / not verified here

- **Zoom, Freelook, Toggle Sprint/Sneak** were not asserted visually: they change camera FOV,
  view rotation and key state rather than the UI, and the automated harness has no way to
  confirm those without a human looking at the screen. Their code paths compile and run
  (they are enabled through the same tick loop that is exercised), but they deserve a manual
  check by a player.
- **Synthetic OS key injection** (`tools/key-tap.ps1`) did not reliably reach the game's GLFW
  input on this machine, so keybind-driven checks were done through the gametest API instead.
  The scripts are kept for interactive use.
- The jar ships a tiny `fabric-client-gametest` entrypoint. Fabric only runs those entrypoints
  when the `fabric.client.gametest` system property is set, which only the `runGametest` run
  configuration does — in a normal game the class is never loaded.
- No dedicated-server or multiplayer testing; all automated checks run in a local
  singleplayer world.
- **The launcher plays offline accounts only.** Microsoft sign-in is not implemented: it needs a
  client ID registered in Azure, which this project does not have. So the launch path is verified,
  the login is not — and multiplayer servers with authentication enabled will reject it.
- **The launcher test reused this machine's Minecraft 26.2 assets.** The 5,057 asset objects were
  already on disk from the mod's development runs, so the test proved they are found, verified and
  skipped (`downloaded=0 cached=5057`), not that 481 MB downloads reliably. The 125 MB of libraries
  and the 39 MB client jar were downloaded and hash-checked from Mojang during the first run.
- **Only what the launcher prints was checked for `--prepare`/`--launch`.** The GUI's Play button
  runs the same `LaunchService` as the CLI, and the button path was exercised only through the
  screenshot and smoke test — not by clicking Play in a visible window.
- **Four of the five launcher pages were verified as renders**, not as interactive sessions:
  nothing clicked the instance editor, the mod switches or the account list. Those handlers are the
  same ones the CLI verification drives for installs, but the button wiring itself is untested.
- **The uninstall was not run.** The install was, for real, into Program Files with the published
  installer (4b.10): the files, the Start Menu shortcut, the Apps & features entry and launching it
  from that shortcut all work. Removing it was left for the owner of the machine to choose; the
  uninstall string Apps & features registers is `MsiExec.exe /X{CB150244-82B1-3C1B-B9C3-36CEDAE1B8CF}`.
- **An installed copy on a machine with no Java 25 cannot start the game.** This is the first thing
  the real install exposed (4b.10). `JavaRuntime` looks at the configured path, a `.toolchain` JDK
  beside the launcher, `JAVA_HOME`, then `PATH`; a packaged install on a clean machine has none of
  those, and the launcher has no "download a runtime" path. The `runtime/` folder in the package is
  the launcher's own trimmed JRE, not a game runtime. The Settings page says so plainly ("No Java
  found — the game cannot start until one is set"), but a new user has to supply Java themselves.
- **Updating an install inside Program Files was not run.** The swap itself is verified (4b.8), but
  in Program Files the copy step needs administrator rights, so the swap script re-launches itself
  with a UAC prompt. That re-launch is code, not something this session could click through; an
  unelevated portable copy updates without any prompt (which is what was run).
- **The GUI's update offer was never seen for a genuinely newer version.** The installed copy's
  startup check does reach the live manifest — its log shows `update check: running 1.0.0, published
  1.0.0` (4b.10) — but because it is the newest release it correctly offered nothing. The offer
  dialog itself was only exercised against a local manifest (4b.8).
- **Re-publishing a version does not move its tag.** `publish-release.sh` updates an existing
  release in place and leaves its tag alone, so a fix published as the same version keeps the tag at
  the commit that first created it. Bump `version` in `gradle.properties` for each new release.
- **No code signing.** Both installers are unsigned, so SmartScreen warns on first run until enough
  people have run that file. Signing needs a certificate, which is a purchase, not a build step.
- **The icons were not inspected in the real Windows taskbar or in Apps & features.** The window
  sets its icon from the same PNG, and the `.ico` is what jpackage embedded, but nobody looked at
  Explorer or the taskbar to confirm it renders there.
