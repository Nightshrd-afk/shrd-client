# Shrd Launcher 1.4.0

The Home page, rebuilt to look like the client it launches — and a signed build, so Windows has a
publisher name to show instead of "Unknown publisher".

## New: the Home page

- A taller banner with the player's own skin standing on it, and the name plate above the figure:
  click it to jump straight to the profiles it belongs to.
- A client card in the banner showing the bundled Shrd Client version, with **Manage mods** one click
  away, and a palette button in the corner that goes to Customisation.
- The account chip now sits along the bottom centre of the banner, the way a profile banner does,
  and still switches accounts or starts the Microsoft sign-in.
- **Java / Bedrock** selector beside *Last played profiles*. Java is selected; Bedrock is greyed,
  because this launcher does not run Bedrock Edition and pretending otherwise would be worse than
  saying so.
- Bigger profile cards with a larger Play button, and the bottom bar stretched the way the reference
  layout has it: a wide **Manage all profiles** and an **Add mods** beside it.
- Every page now sits on the client's faint plus-sign texture instead of a flat black background, and
  the banner picks up the accent colour, so a crimson or emerald theme re-tints the banner too.

## New: signed downloads

`launcher/sign-builds.sh` signs the exe, the msi and both jars, and the packaging script runs it, so
a release is signed without anyone remembering to do it.

- With no configuration it generates a self-signed code-signing certificate on first use, kept in
  `.toolchain/signing` (git ignores it), and signs with that. Nothing is written to the Windows
  certificate store, so no administrator prompt and no leftover key.
- With a certificate authority's file it signs with that instead: set `SHRD_PFX` and
  `SHRD_PFX_PASSWORD`, or drop the file at `.toolchain/signing/shrd-codesign.pfx`.

**What this does and does not fix.** Every download now carries a valid Authenticode signature and
`jarsigner` reports the jars verified — check Properties → Digital Signatures on the exe. What it
does not do is silence SmartScreen on a machine that has never seen the file: that needs a paid
certificate from a CA, because Windows only trusts a new root certificate after a person confirms a
dialog. `SIGN-OK` and `VERIFY … signature valid, certificate not trusted by Windows` in the build log
is the honest description of the self-signed build.

## Also

- The home banner and the pages keep working with a custom background image and with every accent on
  the Customisation page.
