Shrd Launcher 1.3.0 — Minecraft launcher for Shrd Client.

New: pick your colour off a wheel
  Customise → Colour theme now has a colour wheel instead of a Windows dialog. Hue runs round the
  rim, saturation fades to white in the middle, and the ring marker shows where you are — any hue at
  any strength is one drag away. The brightness bar underneath is what reaches the darker half: the
  disc is a full-brightness field, so dragging the bar down turns a blue into a navy, and all the way
  down into near-black. The swatch and hex beside the wheel follow the drag, and "Use this colour"
  applies it; the eight presets still apply on a single click.

Fixed
  Dark accents no longer theme their own buttons black-on-black: the launcher checks how bright the
  accent is and switches the text on top of it, so a navy or deep violet theme stays readable.
  Changing the theme no longer jumps the page back to the top — the scroll position survives.

Downloads
  Shrd Launcher-1.3.0.exe          the installer: per-machine, into Program Files, with a
                                   Start Menu entry and an entry in Apps & features
  Shrd Launcher-1.3.0.msi          the same installer as an MSI, for silent or scripted installs
  shrd-launcher-1.3.0-windows-x64.zip
                                   portable: unzip anywhere and run the .exe inside; no admin rights

  Every download carries its own trimmed Java 25, so Java does not have to be installed to run the
  launcher. Starting the game still needs a Java 25 runtime on the machine; Settings says so plainly
  when it cannot find one.

Updating
  An installed launcher checks this release's manifest on startup and offers the new version:
  https://github.com/Nightshrd-afk/shrd-client/releases/latest/download/shrd-launcher-latest.json

Instances, downloaded libraries, assets and logs live in %USERPROFILE%\.shrd-launcher, so updating or
uninstalling the launcher does not touch your worlds, the client's settings, or the theme you picked.

Notes
  This release also carries everything from 1.2.0 for anyone still on 1.1.0: the Customisation page
  itself (theme and background), the sign-in window that ends on the Java Edition account it signed
  in, and the turned, lit player figure. The launcher's theme does not change the in-game client's
  menu, which stays Shrd purple.
