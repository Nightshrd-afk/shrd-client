#!/usr/bin/env bash
# Publishes a Shrd Launcher release to GitHub: builds the downloads, tags the version, creates the
# release and uploads the installer, the portable zip and the update manifest — the three files a
# player downloads and the one file the launcher's own updater reads.
#
#   bash launcher/publish-release.sh [version]
#
# The version defaults to `version` in gradle.properties, which is the same value the build stamps
# into the launcher, so the tag, the artifacts and what the app reports can never disagree.
#
# It then checks what it published rather than assuming the upload worked: it fetches the manifest
# and the portable zip back from the release's own URL, compares SHA-256 hashes with the local
# files, and finally asks the built launcher to update-check itself against the live URL.
#
# The URL the launcher looks for updates at is baked into Updater.DEFAULT_MANIFEST_URL, so this
# script refuses to publish from a repository that does not match it: otherwise every copy in the
# wild would be looking for its updates somewhere that does not exist.
#
# Requires: gh (from .toolchain/gh or PATH) signed in to an account that can push to origin.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk-25.0.5+8}"
GH="${GH:-$ROOT/.toolchain/gh/gh.exe}"
[ -x "$GH" ] || GH="$(command -v gh || true)"
OUT="$ROOT/dist"
UPDATER_SOURCE="$ROOT/launcher/src/main/java/dev/shrd/launcher/update/Updater.java"
APP="$OUT/Shrd Launcher"
EXE="$APP/Shrd Launcher.exe"
RELEASE_DIR="$ROOT/build/release"

section() {
	echo
	echo "== $1 =="
}

fail() {
	echo "publish: $1" >&2
	exit 1
}

# ---------------------------------------------------------------- version and repository

VERSION="${1:-${SHRD_VERSION:-}}"

if [ -z "$VERSION" ]; then
	VERSION="$(grep -E '^version=' "$ROOT/gradle.properties" | head -1 | cut -d= -f2 | tr -d ' \r')"
fi

[ -n "$VERSION" ] || fail "could not work out the version; pass it as the first argument"
TAG="v$VERSION"
ARCHIVE="shrd-launcher-$VERSION-windows-x64.zip"

if ! git -C "$ROOT" remote get-url origin >/dev/null 2>&1; then
	fail "this repository has no origin remote. Create the repository and push once, for example:
  gh repo create <your-name>/shrd-client --public --source=. --remote=origin --push"
fi

SLUG="$(git -C "$ROOT" remote get-url origin | sed -E 's#^.*github\.com[:/]+##; s#\.git$##')"
MANIFEST_URL="https://github.com/$SLUG/releases/latest/download/shrd-launcher-latest.json"
RELEASE_URL="https://github.com/$SLUG/releases/tag/$TAG"

section "release $VERSION from $SLUG"

if [ -z "$GH" ] || ! "$GH" --version >/dev/null 2>&1; then
	fail "no gh CLI found; put it at .toolchain/gh/gh.exe or set GH"
fi

"$GH" auth status >/dev/null 2>&1 || fail "gh is not signed in. Run: \"$GH\" auth login --hostname github.com --git-protocol https --web"

echo "tag:      $TAG"
echo "manifest: $MANIFEST_URL"

# ---------------------------------------------------------------- preconditions

section "the working tree"
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
	git -C "$ROOT" status --short | head -20
	fail "there are uncommitted changes. A release has to exist as a commit: commit or stash them first."
fi
echo "clean, on branch $(git -C "$ROOT" rev-parse --abbrev-ref HEAD) at $(git -C "$ROOT" rev-parse --short HEAD)"

if git -C "$ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
	echo "note: tag $TAG already exists; its release will be updated in place"
fi

section "the update URL baked into the launcher"
BAKED="$(grep -oE 'https://[^"[:space:]]*shrd-launcher-latest\.json' "$UPDATER_SOURCE" | head -1)"

if [ "$BAKED" != "$MANIFEST_URL" ]; then
	fail "Updater.DEFAULT_MANIFEST_URL does not match this repository.
  baked in:  ${BAKED:-nothing found}
  this repo: $MANIFEST_URL
  Set DEFAULT_MANIFEST_URL in $UPDATER_SOURCE to the second value, commit, and publish again."
fi
echo "DEFAULT_MANIFEST_URL matches: $BAKED"

# ---------------------------------------------------------------- build

section "build the downloads ($VERSION)"
SHRD_VERSION="$VERSION" bash "$ROOT/launcher/build-installer.sh" all

for file in "$OUT/Shrd Launcher-$VERSION.exe" "$OUT/Shrd Launcher-$VERSION.msi" "$OUT/$ARCHIVE" "$OUT/shrd-launcher-latest.json"; do
	[ -f "$file" ] || fail "the build did not produce $file"
done

section "the built launcher reports the version it is published as"
REPORTED="$("$EXE" --version | grep -aoE '^SHRD-VERSION [^ ]+' | head -1 | cut -d' ' -f2)"

if [ "$REPORTED" != "$VERSION" ]; then
	fail "the built launcher reports '$REPORTED' but this release is $VERSION"
fi
echo "SHRD-VERSION $REPORTED"

# The manifest is what decides whether every installed copy updates, so it is checked before the
# upload rather than after: an archive name or hash that does not match the zip would break the
# update for everyone who already has the launcher.
LOCAL_SHA="$(sha256sum "$OUT/$ARCHIVE" | cut -d' ' -f1)"
LOCAL_SIZE="$(wc -c < "$OUT/$ARCHIVE" | tr -d ' ')"
MANIFEST_VERSION="$(grep -oE '"version": *"[^"]+"' "$OUT/shrd-launcher-latest.json" | sed -E 's/.*"([^"]+)"$/\1/')"
MANIFEST_SHA="$(grep -oE '"sha256": *"[^"]+"' "$OUT/shrd-launcher-latest.json" | sed -E 's/.*"([^"]+)"$/\1/')"
MANIFEST_SIZE="$(grep -oE '"size": *[0-9]+' "$OUT/shrd-launcher-latest.json" | grep -oE '[0-9]+')"

[ "$MANIFEST_VERSION" = "$VERSION" ] || fail "the manifest says version $MANIFEST_VERSION, expected $VERSION"
[ "$MANIFEST_SHA" = "$LOCAL_SHA" ] || fail "the manifest's sha256 does not match $ARCHIVE"
[ "$MANIFEST_SIZE" = "$LOCAL_SIZE" ] || fail "the manifest's size does not match $ARCHIVE"
echo "manifest agrees with the zip: $MANIFEST_SHA ($LOCAL_SIZE bytes)"

# ---------------------------------------------------------------- publish

section "push and tag"
"$GH" auth setup-git >/dev/null 2>&1 || true
git -C "$ROOT" push origin "$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"

if ! git -C "$ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
	git -C "$ROOT" tag -a "$TAG" -m "Shrd Launcher $VERSION"
fi
git -C "$ROOT" push origin "refs/tags/$TAG"

section "create the release and upload the downloads"
mkdir -p "$RELEASE_DIR"
NOTES_FILE="$ROOT/docs/release-notes-$VERSION.md"

if [ ! -f "$NOTES_FILE" ]; then
	NOTES_FILE="$RELEASE_DIR/notes-$VERSION.md"
	cat > "$NOTES_FILE" <<EOF
Shrd Launcher $VERSION — Minecraft launcher for Shrd Client.

Downloads
  Shrd Launcher-$VERSION.exe          the installer: per-machine, into Program Files, with a
                                       Start Menu entry and an entry in Apps & features
  Shrd Launcher-$VERSION.msi          the same installer as an MSI, for silent or scripted installs
  $ARCHIVE    portable: unzip anywhere and run the .exe inside; no admin rights needed

Every download carries its own trimmed Java 25, so Java does not have to be installed.

Updating
  An installed launcher checks this release's manifest on startup and offers the new version:
  $MANIFEST_URL

Instances, downloaded libraries, assets and logs live in %USERPROFILE%\\.shrd-launcher, so updating
or uninstalling the launcher does not touch your worlds or the client's settings.

Accounts are offline (a name you choose); Microsoft sign-in is not implemented, so servers that
require authentication will reject it. Singleplayer and LAN work.
EOF
fi

ASSETS=(
	"$OUT/Shrd Launcher-$VERSION.exe"
	"$OUT/Shrd Launcher-$VERSION.msi"
	"$OUT/$ARCHIVE"
	"$OUT/shrd-launcher-latest.json"
)

if "$GH" release view "$TAG" >/dev/null 2>&1; then
	echo "release $TAG exists; uploading over it"
	"$GH" release upload "$TAG" "${ASSETS[@]}" --clobber
	"$GH" release edit "$TAG" --title "Shrd Launcher $VERSION" --notes-file "$NOTES_FILE" --latest
else
	"$GH" release create "$TAG" "${ASSETS[@]}" \
		--title "Shrd Launcher $VERSION" \
		--notes-file "$NOTES_FILE" \
		--latest
fi

echo "published $RELEASE_URL"

# ---------------------------------------------------------------- verify what was published

section "the published manifest"
PUBLISHED="$(curl -fsSL "$MANIFEST_URL")" || fail "the manifest is not downloadable at $MANIFEST_URL"
PUB_VERSION="$(printf '%s' "$PUBLISHED" | grep -oE '"version": *"[^"]+"' | sed -E 's/.*"([^"]+)"$/\1/')"
PUB_SHA="$(printf '%s' "$PUBLISHED" | grep -oE '"sha256": *"[^"]+"' | sed -E 's/.*"([^"]+)"$/\1/')"
PUB_FILE="$(printf '%s' "$PUBLISHED" | grep -oE '"file": *"[^"]+"' | sed -E 's/.*"([^"]+)"$/\1/')"
echo "serving: version=$PUB_VERSION file=$PUB_FILE sha256=$PUB_SHA"

[ "$PUB_VERSION" = "$VERSION" ] || fail "the published manifest is version $PUB_VERSION, not $VERSION"
[ "$PUB_FILE" = "$ARCHIVE" ] || fail "the published manifest points at $PUB_FILE, not $ARCHIVE"

section "the published downloads, fetched back over HTTPS"
fetch_sha() {
	curl -fsSL "$1" | sha256sum | cut -d' ' -f1
}

ARCHIVE_URL="https://github.com/$SLUG/releases/latest/download/$PUB_FILE"
REMOTE_SHA="$(fetch_sha "$ARCHIVE_URL")"
echo "$PUB_FILE"
echo "  published sha256 $REMOTE_SHA"
echo "  local     sha256 $LOCAL_SHA"
[ "$REMOTE_SHA" = "$LOCAL_SHA" ] || fail "the published zip does not match the zip that was built"

# GitHub rewrites spaces in uploaded asset names to dots, so the installers are published as
# "Shrd.Launcher-1.0.0.exe". Guessing the URL from the local filename would 404, so the probe uses
# the name the release itself reports, and checks its size: a truncated upload is as broken as a
# missing one.
ASSET_LIST="$("$GH" release view "$TAG" --json assets --jq '.assets[] | .name + " " + (.size|tostring)')"

# A HEAD request is enough for the installers: the zip above already proved the release serves real
# bytes, and these two are 37 MB each.
for installer in "Shrd Launcher-$VERSION.exe" "Shrd Launcher-$VERSION.msi"; do
	REMOTE_NAME="${installer// /.}"
	LOCAL_SIZE="$(wc -c < "$OUT/$installer" | tr -d ' ')"
	LINE="$(printf '%s\n' "$ASSET_LIST" | grep -F "$REMOTE_NAME " || true)"

	[ -n "$LINE" ] || fail "$installer was not uploaded to the release"
	REMOTE_SIZE="${LINE##* }"
	[ "$REMOTE_SIZE" = "$LOCAL_SIZE" ] || fail "$REMOTE_NAME is $REMOTE_SIZE bytes on the release, expected $LOCAL_SIZE"

	code="$(curl -fsSL -o /dev/null -w '%{http_code}' -L -I \
		"https://github.com/$SLUG/releases/latest/download/$REMOTE_NAME")" \
		|| fail "$REMOTE_NAME is not downloadable from the release"
	echo "$REMOTE_NAME  HTTP $code  $REMOTE_SIZE bytes"
	[ "$code" = "200" ] || fail "$REMOTE_NAME is not downloadable from the release"
done

section "the launcher's own update check, against the live release"
# Captured rather than piped: piping into sed would report sed's exit status and hide a failed check.
CHECK_OUT="$("$EXE" --check-update --manifest "$MANIFEST_URL")" || fail "the launcher could not read the live manifest at $MANIFEST_URL"
printf '%s\n' "$CHECK_OUT" | sed 's/^/  /'
printf '%s\n' "$CHECK_OUT" | grep -q 'SHRD-UPDATE-UP-TO-DATE' \
	|| fail "the launcher does not recognise the release it just published as its own version"

echo
echo "Shrd Launcher $VERSION is published at $RELEASE_URL"
