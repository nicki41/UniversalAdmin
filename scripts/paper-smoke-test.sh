#!/usr/bin/env bash
# Boots a real Paper server with the freshly built UniversalAdmin jars and runs
# a set of console commands against it. Exits non-zero if the server does not
# start, a module fails, or UniversalAdmin logs an error.
#
#   scripts/paper-smoke-test.sh <minecraft-version>     e.g. 26.3
#
# Needs: the build output (./gradlew build), curl, jq, java on the PATH (21+
# for 1.21.x, 25 for 26.x). Downloads the newest stable Paper build of that
# version from PaperMC's API and checks its SHA-256. Running it accepts the
# Minecraft EULA (https://aka.ms/MinecraftEULA) for the throwaway test server.
set -euo pipefail

VERSION="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${SMOKE_TEST_DIR:-$ROOT/build/smoke-test/$VERSION}"
API="https://fill.papermc.io/v3/projects/paper"
UA="universaladmin-mc/UniversalAdmin smoke test"

rm -rf "$WORK"
mkdir -p "$WORK/plugins/UniversalAdmin"

BUILD_JSON="$(curl -fsS -A "$UA" "$API/versions/$VERSION/builds")"
DOWNLOAD="$(jq -r '[.[] | select(.channel == "STABLE")][0] // .[0] | .downloads["server:default"]' <<< "$BUILD_JSON")"
JAR_NAME="$(jq -r '.name' <<< "$DOWNLOAD")"
curl -fsS -A "$UA" -o "$WORK/$JAR_NAME" "$(jq -r '.url' <<< "$DOWNLOAD")"
echo "$(jq -r '.checksums.sha256' <<< "$DOWNLOAD")  $WORK/$JAR_NAME" | sha256sum -c -

cp "$ROOT"/build/libs/universaladmin-core-*.jar "$WORK/plugins/"
cp "$ROOT"/extensions/discord/build/libs/universaladmin-discord-*.jar "$WORK/plugins/"
echo "eula=true" > "$WORK/eula.txt"
printf 'server-port=25599\nonline-mode=false\nview-distance=4\nsimulation-distance=4\n' > "$WORK/server.properties"
printf 'telemetry:\n  enabled: false\nupdate:\n  check-for-updates: false\n' > "$WORK/plugins/UniversalAdmin/config.yml"

COMMANDS=(
  "admin modules" "admin status" "admin help" "admin worlds" "admin world info world" "admin settings list"
  "admin perf" "admin audit" "admin reports" "admin chat status" "admin wl list" "admin maintenance status"
  "admin server status" "admin world world time day" "admin world world border 5000" "admin entities"
  "admin discord status"
)

LOG="$WORK/server.log"
(
  # Wait for the server to finish starting, then feed the commands and stop it.
  for _ in $(seq 1 300); do
    sleep 2
    grep -q 'Done (' "$LOG" 2>/dev/null && break
  done
  for command in "${COMMANDS[@]}"; do
    echo "$command"
    sleep 1
  done
  sleep 5
  echo "stop"
) | (cd "$WORK" && timeout 900 java -Xmx1536M -jar "$JAR_NAME" --nogui) > "$LOG" 2>&1 || true

CLEAN_LOG="$(sed 's/\x1b\[[0-9;]*m//g' "$LOG")"
FAILED=0
if ! grep -q 'Done (' <<< "$CLEAN_LOG"; then
  echo "::error::Paper $VERSION did not finish starting."
  FAILED=1
fi
MODULES="$(grep -oE 'Active modules \(([0-9]+)\)' <<< "$CLEAN_LOG" | head -1 | grep -oE '[0-9]+' || echo 0)"
if [ "$MODULES" -lt 10 ]; then
  echo "::error::Only $MODULES UniversalAdmin modules became active on Paper $VERSION (expected 10)."
  FAILED=1
fi
PROBLEMS="$(grep -E 'UniversalAdmin|universaladmin' <<< "$CLEAN_LOG" \
  | grep -E 'Exception|ERROR|SEVERE|NoSuchMethod|NoClassDef|failed|FAILED' \
  | grep -vE 'webhook-url' || true)"
if [ -n "$PROBLEMS" ]; then
  echo "::error::UniversalAdmin reported problems on Paper $VERSION:"
  echo "$PROBLEMS"
  FAILED=1
fi
if grep -q '\[missing:' <<< "$CLEAN_LOG"; then
  echo "::error::Missing texts on Paper $VERSION:"
  grep -o '\[missing: [^]]*\]' <<< "$CLEAN_LOG" | sort -u
  FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
  echo "--- server log (tail) ---"
  tail -80 "$LOG"
  exit 1
fi
echo "UniversalAdmin works on Paper $VERSION ($JAR_NAME, $MODULES modules)."
