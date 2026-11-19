#!/usr/bin/env bash
# Tests for build-test-package.sh using a fake plugin repo and a fake jar,
# so no gradle build or network is needed.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/build-test-package.sh"
T="$(mktemp -d "${TMPDIR:-/tmp}/tp-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
fails=0
ok() { echo "ok   - $1"; }
bad() { echo "FAIL - $1"; fails=$((fails + 1)); }

mkdir -p "$T/plugin" "$T/out"
printf 'displayName=Weird & Name/1\r\nauthor=x\r\ndescription=Does A & B with \\1 and /slashes\r\nplugins=a.B\r\n' > "$T/plugin/runelite-plugin.properties"
printf 'PK-not-really-a-jar' > "$T/fake.jar"

# 1. happy path
bash "$SCRIPT" "$T/plugin" --jar "$T/fake.jar" --out "$T/out" >/dev/null
zip="$(ls "$T"/out/*.zip)"
case "$(basename "$zip")" in weird-name-1-test-*.zip) ok "zip named from displayName" ;; *) bad "zip name: $zip" ;; esac
unzip -q "$zip" -d "$T/x"
d="$(ls -d "$T"/x/*)"
[ -f "$d/weird-name-1.jar" ] && ok "jar copied" || bad "jar missing"
grep -q 'weird-name-1.jar' "$d/START.bat" && ok "bat points at jar" || bad "bat jar name"
grep -q 'Weird & Name/1' "$d/README.txt" && ok "special chars in name survive" || bad "name mangled"
grep -qF 'Does A & B with \1 and /slashes' "$d/README.txt" && ok "special chars in description survive" || bad "description mangled"
grep -q '__[A-Z_]*__' "$d/START.bat" "$d/README.txt" && bad "placeholder left" || ok "no placeholders left"
[ "$(head -c 1 "$d/START.bat" | od -An -c | tr -d ' ')" = "@" ] && ok "bat starts cleanly" || bad "bat start"
grep -q $'\r$' "$d/START.bat" && ok "bat is CRLF" || bad "bat not CRLF"
[ "$(grep -c $'\r\r' "$d/START.bat")" = "0" ] && ok "no doubled CR" || bad "doubled CR"

# 2. not a plugin repo
mkdir "$T/empty"
if bash "$SCRIPT" "$T/empty" --jar "$T/fake.jar" --out "$T/out" >/dev/null 2>&1; then bad "empty dir accepted"; else ok "non-plugin dir rejected"; fi

# 3. missing jar
if bash "$SCRIPT" "$T/plugin" --jar "$T/nope.jar" --out "$T/out" >/dev/null 2>&1; then bad "missing jar accepted"; else ok "missing jar rejected"; fi

# 4. non-jar file
printf x > "$T/x.txt"
if bash "$SCRIPT" "$T/plugin" --jar "$T/x.txt" --out "$T/out" >/dev/null 2>&1; then bad "non-jar accepted"; else ok "non-.jar rejected"; fi

# 5. unknown option
if bash "$SCRIPT" "$T/plugin" --bogus >/dev/null 2>&1; then bad "unknown option accepted"; else ok "unknown option rejected"; fi

[ "$fails" -eq 0 ] && { echo "all passed"; exit 0; } || { echo "$fails failed"; exit 1; }
