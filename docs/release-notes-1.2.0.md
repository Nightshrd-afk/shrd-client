Shrd Launcher 1.2.0 — Minecraft launcher for Shrd Client.

New: Customisation
  A new page in the rail for the two things that are purely your taste.

  Colour theme — eight presets (Shrd purple, violet, crimson, ember, emerald, toxic, cyan, ice), a
  hex box, and the Windows colour picker for anything else. A theme is one accent colour and every
  panel, row, border, gradient and button is derived from it, so the whole launcher changes rather
  than one highlighted button. It applies immediately, without a restart.

  Background — any PNG or JPG. It is copied into the launcher's folder, shown full size in the Home
  banner, and blurred and dimmed behind every page so text stays readable. "Remove background" goes
  back to the theme's own gradient.

New: the sign-in window
  The sign-in is now its own window instead of a form buried in Settings: one dark card with the
  name over its top edge, a single large Sign In button and a Sign Up link, the way a launcher's
  front door should look. Signing in with Microsoft runs the same chain the official launcher does
  and ends on the Java Edition account it got back — name, UUID and skin — which becomes the account
  Play uses, so servers that verify sessions accept it.

Better: the player figure
  The character on the Home banner and the sign-in card is drawn as six turned, separately lit boxes
  (front, side and top faces visible, seams shaded, three-times supersampled and scaled down) instead
  of a flat front view.

Downloads
  Shrd Launcher-1.2.0.exe          the installer: per-machine, into Program Files, with a
                                   Start Menu entry and an entry in Apps & features
  Shrd Launcher-1.2.0.msi          the same installer as an MSI, for silent or scripted installs
  shrd-launcher-1.2.0-windows-x64.zip
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
  Servers that verify accounts need a free Azure application (client) id, which the sign-in window
  walks you through once. Offline accounts still play singleplayer and LAN. The launcher's theme does
  not change the in-game client's menu, which stays Shrd purple.
