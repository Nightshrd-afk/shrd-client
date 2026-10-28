#!/usr/bin/env bash
# Code-signs Shrd Launcher's downloads, so Windows can name the publisher instead of saying
# "Unknown publisher".
#
#   bash launcher/sign-builds.sh "dist/Shrd Launcher-1.4.0.exe" "dist/Shrd Launcher-1.4.0.msi"
#   bash launcher/sign-builds.sh          # signs everything in dist/ for the manifest's version
#
# Where the certificate comes from:
#
#   SHRD_PFX=cert.pfx SHRD_PFX_PASSWORD=…   a certificate bought from a CA. That is the only kind
#                                           that removes the SmartScreen warning on a machine that
#                                           has never seen the file before.
#   (nothing set)                           a self-signed code-signing certificate, generated once
#                                           into .toolchain/signing. The file is genuinely signed
#                                           and the signature checks out; Windows still calls the
#                                           publisher untrusted, because trusting a new root is a
#                                           dialog only a human can answer.
#
# Nothing here writes to the Windows certificate store, asks for administrator rights or can block on
# a prompt: the key pair is read straight from the .pfx and kept in memory while signing.
set -euo pipefail
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk-25.0.5+8}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
KEYTOOL="$JAVA_HOME/bin/keytool"
JARSIGNER="$JAVA_HOME/bin/jarsigner"
SIGN_DIR="${SHRD_SIGN_DIR:-$ROOT/.toolchain/signing}"
PFX="$SIGN_DIR/shrd-codesign.pfx"
CER="$SIGN_DIR/shrd-codesign.cer"
PASSWORD_FILE="$SIGN_DIR/password.txt"

section() {
	echo
	echo "== $1 =="
}

fail() {
	echo "SIGN-FAIL $1"
	exit 1
}

# Java and PowerShell are native Windows programs: they cannot resolve an MSYS path such as
# /c/Users/..., so every path handed to them is converted first.
winpath() {
	if command -v cygpath >/dev/null 2>&1; then
		cygpath -w "$1"
	else
		printf '%s' "$1"
	fi
}

[ -x "$KEYTOOL" ] || fail "no keytool at $KEYTOOL — set JAVA_HOME"
[ -x "$JARSIGNER" ] || fail "no jarsigner at $JARSIGNER — set JAVA_HOME"

TARGETS=("$@")

if [ ${#TARGETS[@]} -eq 0 ]; then
	VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$ROOT/dist/shrd-launcher-latest.json" 2>/dev/null | head -1)"

	[ -n "$VERSION" ] || fail "no files given and dist/shrd-launcher-latest.json has no version"

	for candidate in "$ROOT/dist/Shrd Launcher-$VERSION.exe" "$ROOT/dist/Shrd Launcher-$VERSION.msi" \
		"$ROOT/launcher/build/libs/shrd-launcher-$VERSION.jar" "$ROOT/build/libs/shrd-client-$VERSION.jar"; do
		[ -f "$candidate" ] && TARGETS+=("$candidate")
	done

	[ ${#TARGETS[@]} -gt 0 ] || fail "no $VERSION artifacts found to sign"
fi

EXES=()
JARS=()

for target in "${TARGETS[@]}"; do
	[ -f "$target" ] || fail "missing $target"

	case "$target" in
		*.jar) JARS+=("$target") ;;
		*) EXES+=("$target") ;;
	esac
done

# ------------------------------------------------------------------ the key pair

section "code-signing key"
mkdir -p "$SIGN_DIR"

if [ -n "${SHRD_PFX:-}" ]; then
	PFX="$SHRD_PFX"
	PASSWORD="${SHRD_PFX_PASSWORD:-}"
	ALIAS="${SHRD_PFX_ALIAS:-}"
	echo "using the certificate in $PFX"
else
	if [ ! -f "$PASSWORD_FILE" ]; then
		# The password only protects a key that never leaves this machine, and both live in
		# .toolchain/, which git ignores.
		head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' >"$PASSWORD_FILE"
	fi

	PASSWORD="$(cat "$PASSWORD_FILE")"
	ALIAS="shrd"

		# This key pair is generated, disposable and only ever used from here, so a keystore that does
		# not open with the password beside it (an interrupted run can leave the two out of step) is
		# replaced rather than allowed to fail a release. A bought certificate is never treated this
		# way — a wrong password there is a real problem and has to be reported.
		if [ -f "$PFX" ] && ! "$KEYTOOL" -list -keystore "$(winpath "$PFX")" -storetype PKCS12 -storepass "$PASSWORD" >/dev/null 2>&1; then
			echo "the stored signing certificate does not open with its password — generating a new one"
			rm -f "$PFX" "$CER"
		fi

		if [ ! -f "$PFX" ]; then
		echo "generating a self-signed code-signing certificate (once)"
		"$KEYTOOL" -genkeypair \
			-alias "$ALIAS" \
			-keyalg RSA -keysize 3072 -sigalg SHA256withRSA \
			-dname "CN=Shrd Client, O=Shrd Client" \
			-ext "eku=codeSigning" \
			-validity 1825 \
			-storetype PKCS12 -keystore "$(winpath "$PFX")" \
			-storepass "$PASSWORD" -keypass "$PASSWORD" >/dev/null 2>&1 \
			|| fail "keytool could not create the key pair"
	fi
fi

PFX_WIN="$(winpath "$PFX")"

if [ -z "$ALIAS" ]; then
	ALIAS="$("$KEYTOOL" -list -keystore "$PFX_WIN" -storetype PKCS12 -storepass "$PASSWORD" 2>/dev/null \
		| sed -n 's/^\([^,]*\),.*PrivateKeyEntry.*/\1/p' | head -1 | tr -d '\r')"
fi

[ -n "$ALIAS" ] || fail "no private key found in $PFX"

echo "key alias $ALIAS"

# ------------------------------------------------------------------ exe / msi

if [ ${#EXES[@]} -gt 0 ]; then
	section "signing the installers"
	LIST=""

	for exe in "${EXES[@]}"; do
		LIST="$LIST$(winpath "$(cd "$(dirname "$exe")" && pwd)/$(basename "$exe")");"
	done

	OUT="$(powershell -NoProfile -ExecutionPolicy Bypass -File "$(winpath "$ROOT/tools/sign-windows.ps1")" \
		-List "$LIST" -Pfx "$PFX_WIN" -PfxPassword "$PASSWORD" -ExportCer "$(winpath "$CER")" 2>&1)" || {
		printf '%s\n' "$OUT" | sed 's/^/  /'
		fail "signing failed"
	}
	printf '%s\n' "$OUT" | sed 's/^/  /'
fi

# ------------------------------------------------------------------ jars

if [ ${#JARS[@]} -gt 0 ]; then
	section "signing the jars"

	for jar in "${JARS[@]}"; do
		# -signedjar writes beside the original and only replaces it on success, so a failed run
		# cannot leave a half-signed jar behind.
		JAR_OUT="$("$JARSIGNER" -keystore "$PFX_WIN" -storetype PKCS12 -storepass "$PASSWORD" \
			-sigalg SHA256withRSA -digestalg SHA-256 \
			-signedjar "$(winpath "$jar.signed")" "$(winpath "$jar")" "$ALIAS" 2>&1)" \
			|| {
			printf '%s\n' "$JAR_OUT" | sed 's/^/  /'
			fail "jarsigner rejected $jar"
		}
		mv -f "$jar.signed" "$jar"
		VERIFY_OUT="$("$JARSIGNER" -verify "$(winpath "$jar")" 2>&1)" || true

		case "$VERIFY_OUT" in
			*"jar verified"*) ;;
			*)
				printf '%s\n' "$VERIFY_OUT" | sed 's/^/  /'
				fail "$jar does not verify"
				;;
		esac

		echo "  SIGNED $jar"
		echo "  VERIFY $jar jar verified"
	done
fi

echo
echo "SIGN-OK signed ${#EXES[@]} installer(s) and ${#JARS[@]} jar(s)"
echo "  certificate: $PFX"
echo "  public half: $CER (double-click it and install into Trusted Root to see the publisher name)"
