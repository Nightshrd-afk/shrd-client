# Shrd Launcher 1.5.0

Sign-in with nothing to set up, a cleaner rail, and a faster in-game HUD.

## Sign in, with no setup at all

The launcher used to open a "One-time setup" card asking for an Azure application id before it would
let anyone sign in. That card is gone: the launcher now carries Microsoft's public device-code client
id, the same public identifier third-party Minecraft launchers have used for the device flow for
years, with no secret and no account of anyone's involved.

- **SIGN IN** opens the Microsoft page, shows a short code, and comes back with the real Java Edition
  account: name, Mojang UUID, skin and an access token, which is what an online server checks.
- Nothing is registered anywhere, nothing is pasted anywhere, and no password is ever typed into this
  window — Microsoft's own page is the only place you type one.
- Settings keeps a **Client id** field for anyone who would rather Microsoft's consent page named
  their own application. It can stay blank, and leaving it blank is the normal case.

## A cleaner rail and a cleaner launcher

- The seven rail icons were drawn again on one 24-unit grid with a single stroke weight and the same
  optical margin, so they read as a set. Home, Instances, Mods, Servers, Console, Customise and
  Settings: no more icon that looked like a smiley, no more cog that looked like a sun.
- The selected page is a filled accent tile with the glyph on it and the label underneath, instead
  of a coloured bar beside a full-width row.
- Settings uses a sliders icon and Customise a proper palette with a thumb notch, both legible at
  rail size.

## 400 FPS: what actually moves the number

The honest answer, measured on this project's own benchmark at 854x480 with render distance 16:

| Phase | Settings | Median FPS |
|---|---|---|
| A2 | overlays off, VSync off, frame limit at "unlimited" | 461 |
| B2 | overlays on, same settings | 433 |
| C2 | Performance Mode on, drop shadows on | 405 |
| **C3** | **Performance Mode on, Fast HUD on** | **575** |
| E1 | VSync on, frame limit 120 (a stock install) | 60 |

Three things this says:

1. **VSync and the 120 fps frame limit are the usual ceiling.** Phase E1 is a stock install: 60 fps,
   because the monitor is 60 Hz and VSync waits for it. Performance Mode turns both off.
2. **Minecraft's "260" frame limit is not a 260 fps cap.** It is vanilla's *unlimited* value
   (`Options.UNLIMITED_FRAMERATE_CUTOFF`), the point where the limiter stops being called at all.
3. **Above ~400 fps the overlays are the limit, not the graphics settings.** With the world settings
   already at their floor, Performance Mode alone barely moved the number (405 vs 433 either side of
   it). Drawing the HUD without per-string drop shadows and with square panel corners took the same
   scene from 405 to 575 fps, about 42%, against a control phase on each side.

So **Fast HUD** is new and on by default inside Performance Mode: same layout, same colours, no
shadowed text. Turn it off in the mod menu if you want the shadows back.

What no launcher can promise is a number on someone else's machine: at 1080p, with a heavier scene and
a slower GPU, the ceiling is the graphics card. What this release does is remove the software limits
and the per-frame waste that stop a fast machine reaching its own ceiling.
