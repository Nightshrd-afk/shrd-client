#!/usr/bin/env bash
# Builds everything a Shrd Launcher release is made of, for Windows x64.
#
#   dist/Shrd Launcher-1.0.0.exe        installer: one file, wizard, Program Files, Start Menu
#   dist/Shrd Launcher-1.0.0.msi        the same installer as an MSI (group policy, silent install)
#   dist/shrd-launcher-1.0.0-windows-x64.zip   portable: unpack anywhere and double-click
#   dist/Shrd Launcher/                 the portable build, already unpacked
#   dist/shrd-launcher-latest.json      the update manifest the launcher compares itself against
#
# All three install nothing else: the package carries its own trimmed Java runtime, so the machine
# does not need Java. The MSI and the EXE need the WiX Toolset on PATH to be built, which this
# script downloads into .toolchain/wix-3.14.1 if it is not already there.
#
# Usage: bash launcher/build-installer.sh [all|portable|msi|exe]
# Env:   SHRD_VERSION=1.1.0      build a different version (the update test uses this)
#        SHRD_OUT=dist-test      write the artifacts somewhere else
set -euo pipefail

TARGETS="${1:-all}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk-25.0.5+8}"
GRADLE="${GRADLE:-$ROOT/.toolchain/gradle-9.8.2/bin/gradle}"
JAVA="$JAVA_HOME/bin/java"
# Gradle and jpackage both look up their own tools, so the JDK has to be in the environment too.
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
VERSION="${SHRD_VERSION:-1.0.0}"
NAME="Shrd Launcher"
OUT="$ROOT/${SHRD_OUT:-dist}"
ARCHIVE="shrd-launcher-$VERSION-windows-x64.zip"
LAUNCHER_JAR_NAME="shrd-launcher-$VERSION.jar"
MOD_JAR_NAME="shrd-client-$VERSION.jar"
LAUNCHER_JAR="$ROOT/launcher/build/libs/$LAUNCHER_JAR_NAME"
MOD_JAR="$ROOT/build/libs/$MOD_JAR_NAME"
STAGE="$OUT/stage"
APP="$OUT/$NAME"
WIX="$ROOT/.toolchain/wix-3.14.1"
WIX_URL="https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
# Fixed, and must never change: it is how Windows knows a new release upgrades the old install
# instead of sitting beside it. Changing it would leave two copies on every machine that updates.
UPGRADE_UUID="8f2c41d6-9a37-4f0b-8a53-1d7c6b90e24a"

section() {
	echo
	echo "== $1 =="
}

wants() {
	[ "$TARGETS" = "all" ] || [ "$TARGETS" = "$1" ]
}

case "$TARGETS" in
	all | portable | msi | exe) ;;
	*)
		echo "unknown target '$TARGETS' — use all, portable, msi or exe"
		exit 1
		;;
esac

if [ ! -x "$JAVA" ]; then
	echo "no JDK at $JAVA — set JAVA_HOME"
	exit 1
fi

mkdir -p "$OUT"

section "build the launcher and the mod ($VERSION)"
(cd "$ROOT/launcher" && "$GRADLE" build --console=plain -q -Pversion="$VERSION")
(cd "$ROOT" && "$GRADLE" build --console=plain -q -Pversion="$VERSION")
[ -f "$LAUNCHER_JAR" ] || { echo "missing $LAUNCHER_JAR"; exit 1; }
[ -f "$MOD_JAR" ] || { echo "missing $MOD_JAR"; exit 1; }
echo "launcher jar: $(wc -c < "$LAUNCHER_JAR") bytes"
echo "mod jar:      $(wc -c < "$MOD_JAR") bytes"

section "application icon"
(cd "$ROOT" && "$JAVA" tools/make-icon.java)
echo "icon: $(wc -c < "$ROOT/launcher/icon.ico") bytes"

section "trimmed Java runtime (jlink)"
rm -rf "$STAGE"
mkdir -p "$STAGE/input/mods"
cp "$LAUNCHER_JAR" "$STAGE/input/"
# The client mod travels inside the app, so "Install Shrd Client" on the Mods page works on a
# machine that has never seen this repository. jpackage copies input/ into the app's folder, so
# this lands at app/mods/ — where ModsManager looks for a bundled jar.
cp "$MOD_JAR" "$STAGE/input/mods/"
"$JAVA_HOME/bin/jlink" \
	--add-modules java.base,java.desktop,java.logging,java.net.http,java.prefs,java.xml,jdk.crypto.ec,jdk.crypto.mscapi,jdk.unsupported \
	--strip-debug \
	--no-header-files \
	--no-man-pages \
	--compress zip-6 \
	--output "$STAGE/runtime"
echo "runtime size: $(du -sh "$STAGE/runtime" | cut -f1)"

JPACKAGE_ARGS=(
	--name "$NAME"
	--app-version "$VERSION"
	--vendor "Shrd Client"
	--description "Shrd Launcher - Minecraft launcher for Shrd Client"
	--input "$STAGE/input"
	--main-jar "$LAUNCHER_JAR_NAME"
	--main-class dev.shrd.launcher.ShrdLauncher
	--runtime-image "$STAGE/runtime"
	--icon "$ROOT/launcher/icon.ico"
	--java-options "-Xmx512m"
	--dest "$OUT"
)

section "portable app image (jpackage)"
rm -rf "$APP"
"$JAVA_HOME/bin/jpackage" --type app-image "${JPACKAGE_ARGS[@]}"
echo "built $APP"
ls "$APP/app/mods" | sed 's/^/bundled mod: /'

if wants msi || wants exe; then
	section "WiX Toolset (needed for .msi and .exe installers)"
	if [ -x "$WIX/candle.exe" ]; then
		echo "using $WIX"
	else
		echo "downloading $WIX_URL"
		mkdir -p "$WIX"
		TMP_ZIP="$ROOT/.toolchain/wix314-binaries.zip"
		curl -L --fail --silent --show-error -o "$TMP_ZIP" "$WIX_URL"
		echo "unpacking into $WIX"
		# The JDK's jar tool unpacks a zip, so this works on a Windows box with no unzip binary.
		"$JAVA_HOME/bin/jar" --extract --file "$TMP_ZIP" -C "$WIX"
		rm -f "$TMP_ZIP"
	fi
	export PATH="$WIX:$PATH"
	"$WIX/candle.exe" -? 2>&1 | head -1
fi

# Per-machine install into C:\Program Files\Shrd Launcher, with a Start Menu entry in a
# "Shrd Launcher" group and an entry in Apps & features that uninstalls it. Installing asks for
# administrator rights, which is what putting an app in Program Files costs on Windows.
INSTALLER_ARGS=(
	"${JPACKAGE_ARGS[@]}"
	--win-menu
	--win-menu-group "$NAME"
	--win-dir-chooser
	--win-upgrade-uuid "$UPGRADE_UUID"
)

if wants msi; then
	section "installer (.msi)"
	rm -f "$OUT/$NAME-$VERSION.msi"
	"$JAVA_HOME/bin/jpackage" --type msi "${INSTALLER_ARGS[@]}"
	ls -la "$OUT/$NAME-$VERSION.msi" | awk '{ print "msi: " $9 " (" $5 " bytes)" }'
fi

if wants exe; then
	section "installer (.exe)"
	rm -f "$OUT/$NAME-$VERSION.exe"
	# The exe is the same installer wrapped in a bootstrapper, which is the friendlier download:
	# it can install the MSI for the user instead of asking them to right-click and choose Install.
	"$JAVA_HOME/bin/jpackage" --type exe "${INSTALLER_ARGS[@]}"
	ls -la "$OUT/$NAME-$VERSION.exe" | awk '{ print "exe: " $9 " (" $5 " bytes)" }'
fi

if wants portable; then
	section "portable readme"
	cat > "$APP/INSTALL.txt" <<EOF
Shrd Launcher $VERSION - portable Windows build
$(printf '=%.0s' $(seq 1 $((29 + ${#VERSION}))))

1. Unzip this folder somewhere you can write to, e.g. C:\\Users\\<you>\\Shrd Launcher
2. Double-click "Shrd Launcher.exe"
3. On the Home page, press PLAY.

There is nothing to install and no admin rights are needed: the runtime folder contains the
launcher's own Java 25, so Java does not have to be on your machine at all.

Prefer an installer that puts this in Program Files and adds a Start Menu entry? Download
"$NAME-$VERSION.exe" instead of the zip.

The client mod is included at app\\mods\\$MOD_JAR_NAME, so "Install Shrd Client" on the Mods
page works without any build tools. "Get Fabric API" fetches Fabric API from Modrinth.

Where your files go
  %USERPROFILE%\\.shrd-launcher          instances, downloaded libraries and assets, logs
  ...\\shrd-launcher.json                accounts, instances and settings

Optional: right-click "Create Desktop Shortcut.ps1" -> Run with PowerShell to put a shortcut on
your desktop. Windows may ask whether to trust the script; that is a normal PowerShell prompt,
not something this launcher needs.

Accounts are offline (a name you choose). Microsoft sign-in is not implemented, so servers that
require authentication will reject it; singleplayer and LAN work.
EOF

	cat > "$APP/Create Desktop Shortcut.ps1" <<'EOF'
$exe = Join-Path $PSScriptRoot 'Shrd Launcher.exe'
$link = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Shrd Launcher.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($link)
$shortcut.TargetPath = $exe
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.IconLocation = $exe
$shortcut.Description = 'Shrd Launcher'
$shortcut.Save()
Write-Host "Created $link"
EOF

	section "zip (the portable download)"
	rm -f "$OUT/$ARCHIVE"
	# The JDK's own jar tool writes a plain zip, which avoids translating POSIX paths for PowerShell.
	"$JAVA_HOME/bin/jar" --create --file "$OUT/$ARCHIVE" -C "$OUT" "$NAME"
	echo "zip: $OUT/$ARCHIVE ($(wc -c < "$OUT/$ARCHIVE") bytes)"
fi

section "update manifest"
# What the launcher's own update check reads. url is left out on purpose: the archive sits next to
# this file, and the launcher resolves it relative to wherever the manifest was fetched from — so
# the same release works from a web server, a release page or a shared folder.
ZIP="$OUT/$ARCHIVE"

if [ -f "$ZIP" ]; then
	SHA=$(cd "$OUT" && sha256sum "$ARCHIVE" | cut -d' ' -f1)
	SIZE=$(wc -c < "$ZIP")
	cat > "$OUT/shrd-launcher-latest.json" <<EOF
{
  "version": "$VERSION",
  "file": "$ARCHIVE",
  "url": "",
  "sha256": "$SHA",
  "size": $SIZE,
  "notes": "Shrd Launcher $VERSION"
}
EOF
	echo "manifest: $OUT/shrd-launcher-latest.json"
	cat "$OUT/shrd-launcher-latest.json"
else
	echo "no portable zip built in this run, so no manifest was written (build it with: portable)"
fi

rm -rf "$STAGE"

echo
echo "artifacts in $OUT"
ls -la "$OUT" | grep -Ev '^total|^d' || true
