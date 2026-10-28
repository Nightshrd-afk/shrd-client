#!/usr/bin/env bash
# Drives Shrd Launcher the way a player would — install the mod, prepare, launch — and asserts what
# the game actually reported. Exits non-zero if any check fails.
#
# This launches a real Minecraft window (that is the point of the check), then closes it again.
#
# Usage: tools/verify-launcher.sh [instance-name] [wait-seconds]
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk-25.0.5+8}"
JAVA="$JAVA_HOME/bin/java"
JAR="$ROOT/launcher/build/libs/shrd-launcher-1.0.0.jar"
INSTANCE="${1:-Default}"
WAIT="${2:-70}"

failures=0
check() {
	if [ "$2" = "1" ]; then
		echo "  PASS  $1${3:+ ($3)}"
	else
		echo "  FAIL  $1${3:+ ($3)}"
		failures=$((failures + 1))
	fi
}

contains() { grep -qa -- "$2" "$1" && echo 1 || echo 0; }
count_of() { grep -ac -- "$2" "$1" 2>/dev/null || echo 0; }
first_timestamp() { grep -aoE '^\[[0-9]{2}:[0-9]{2}:[0-9]{2}\]' "$1" 2>/dev/null | head -1 | tr -d '[]'; }

if [ ! -f "$JAR" ]; then
	echo "no launcher jar at $JAR"
	echo "build it with: (cd launcher && ../.toolchain/gradle-9.8.2/bin/gradle build)"
	exit 1
fi

echo "== install mods through the launcher =="
mods_out="$("$JAVA" -jar "$JAR" --install-mods "$INSTANCE" 2>&1)"
mods_status=$?
echo "$mods_out" | grep -aE "SHRD-MOD" | sed 's/^/  /'
check "install-mods exit status" "$([ "$mods_status" = "0" ] && echo 1 || echo 0)" "status $mods_status"
check "Shrd Client copied into the instance" "$(echo "$mods_out" | grep -qa 'SHRD-MOD client=shrd-client' && echo 1 || echo 0)"
check "Fabric API fetched" "$(echo "$mods_out" | grep -qa 'SHRD-MOD fabric-api=fabric-api' && echo 1 || echo 0)"

echo
echo "== prepare: resolve metadata, download every file =="
prep_out="$("$JAVA" -jar "$JAR" --prepare "$INSTANCE" 2>&1)"
prep_status=$?
echo "$prep_out" | grep -aE "SHRD-PLAN version|SHRD-PLAN main|SHRD-PLAN libraries|SHRD-DOWNLOAD |SHRD-PREPARE" | sed 's/^/  /'
check "prepare exit status" "$([ "$prep_status" = "0" ] && echo 1 || echo 0)" "status $prep_status"
check "plan resolved" "$(echo "$prep_out" | grep -qa 'SHRD-PREPARE-OK' && echo 1 || echo 0)"
check "no failed downloads" "$(echo "$prep_out" | grep -qa 'SHRD-DOWNLOAD-FAILED' && echo 0 || echo 1)"

echo
echo "== launch (opens the game window for up to ${WAIT}s, then closes it) =="
launch_out="$("$JAVA" -jar "$JAR" --launch "$INSTANCE" --wait "$WAIT" 2>&1)"
launch_status=$?
launch_log="$(echo "$launch_out" | grep -a 'SHRD-LAUNCH started' | sed 's/.*log=//' | tr -d '\r')"
echo "$launch_out" | grep -aE "SHRD-LAUNCH" | sed 's/^/  /'
check "game process started" "$(echo "$launch_out" | grep -qa 'SHRD-LAUNCH started' && echo 1 || echo 0)"
check "game still running at ${WAIT}s" "$([ "$launch_status" = "2" ] && echo 1 || echo 0)" "status $launch_status"

instance_log="$HOME/.shrd-launcher/instances/$INSTANCE/logs/latest.log"

if [ -n "$launch_log" ] && [ -f "$launch_log" ]; then
	echo "  launcher console log: $launch_log ($(count_of "$launch_log" '') lines)"
	check "launcher captured the game's output" "$([ "$(count_of "$launch_log" '')" -gt 50 ] && echo 1 || echo 0)"
	check "Fabric booted" "$(contains "$launch_log" 'with Fabric Loader')"
	check "shrdclient in Fabric's mod list" "$(contains "$launch_log" '- shrdclient')"
	check "Shrd Client entrypoint ran" "$(contains "$launch_log" 'Shrd Client v1.0.0 loaded')"
	check "exactly one loader banner (no replay, no duplication)" \
		"$([ "$(count_of "$launch_log" 'Loading Minecraft')" = "1" ] && echo 1 || echo 0)" \
		"$(count_of "$launch_log" 'Loading Minecraft') banner(s)"
	check "console events logged once" \
		"$([ "$(count_of "$launch_log" 'Mappings not present')" = "1" ] && echo 1 || echo 0)" \
		"$(count_of "$launch_log" 'Mappings not present') copy(ies)"

	if [ -f "$instance_log" ]; then
		check "first console line is this session's, not a leftover" \
			"$([ -n "$(first_timestamp "$launch_log")" ] && [ "$(first_timestamp "$launch_log")" = "$(first_timestamp "$instance_log")" ] && echo 1 || echo 0)" \
			"console $(first_timestamp "$launch_log") vs game $(first_timestamp "$instance_log")"
		check "game reached the menu" "$(contains "$instance_log" 'Sound engine started')"
	fi
else
	check "launcher console log exists" 0 "$launch_log"
fi

echo
if [ "$failures" = "0" ]; then
	echo "launcher verification: ALL CHECKS PASSED"
else
	echo "launcher verification: $failures CHECK(S) FAILED"
fi

[ "$failures" = "0" ] || exit 1
exit 0
