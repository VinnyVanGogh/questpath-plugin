#!/usr/bin/env bash
#
# Build a friend-ready Windows test zip for any of our RuneLite plugins.
#
#   tools/test-package/build-test-package.sh [PLUGIN_DIR] [options]
#
#   PLUGIN_DIR      plugin repo root (default: current directory)
#   --out DIR       where the zip goes (default: PLUGIN_DIR/build/test-package)
#   --jar FILE      use this fat jar instead of running ./gradlew shadowJar
#   --run-tests     keep the plugin's tests in the build (default: -x test)
#   -h, --help      this text
#
# Works on every plugin cloned from the runelite-plugin-dev template: they all
# define a `shadowJar` task whose Main-Class is the plugin's developer-client
# launcher, so `java -jar <jar> --developer-mode` starts RuneLite with the
# plugin loaded. The zip holds that jar, START.bat and README.txt, rendered
# from template/. Nothing is installed on the friend's machine.
#
# Never launches RuneLite and never touches the game: it only builds a file.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="${TEST_PACKAGE_TEMPLATE_DIR:-$HERE/template}"

usage() { sed -n '3,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
die() { echo "error: $*" >&2; exit 1; }

PLUGIN_DIR=""
OUT_DIR=""
JAR=""
RUN_TESTS=0

while [ $# -gt 0 ]; do
  case "$1" in
    --out) [ $# -ge 2 ] || die "--out needs a value"; OUT_DIR="$2"; shift 2 ;;
    --jar) [ $# -ge 2 ] || die "--jar needs a value"; JAR="$2"; shift 2 ;;
    --run-tests) RUN_TESTS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) die "unknown option: $1" ;;
    *) [ -z "$PLUGIN_DIR" ] || die "only one plugin directory"; PLUGIN_DIR="$1"; shift ;;
  esac
done

PLUGIN_DIR="$(cd "${PLUGIN_DIR:-.}" && pwd)" || die "no such directory"
PROPS="$PLUGIN_DIR/runelite-plugin.properties"
[ -f "$PROPS" ] || die "$PLUGIN_DIR has no runelite-plugin.properties (not a plugin repo?)"
[ -d "$TEMPLATE_DIR" ] || die "template directory missing: $TEMPLATE_DIR"

prop() { # prop KEY -> value from runelite-plugin.properties, CR and edges trimmed
  sed -n "s/^$1=//p" "$PROPS" | head -n1 | tr -d '\r' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

DISPLAY_NAME="$(prop displayName)"
DESCRIPTION="$(prop description)"
[ -n "$DISPLAY_NAME" ] || die "displayName missing in $PROPS"
[ -n "$DESCRIPTION" ] || DESCRIPTION="(no description)"

SLUG="$(printf '%s' "$DISPLAY_NAME" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]\{1,\}/-/g; s/^-//; s/-$//')"
[ -n "$SLUG" ] || die "cannot derive a file name from displayName '$DISPLAY_NAME'"

SHA="nogit"
if git -C "$PLUGIN_DIR" rev-parse --git-dir >/dev/null 2>&1; then
  SHA="$(git -C "$PLUGIN_DIR" rev-parse --short=8 HEAD)"
  if [ -n "$(git -C "$PLUGIN_DIR" status --porcelain --untracked-files=no)" ]; then
    SHA="$SHA-dirty"
  fi
fi
STAMP="$(date -u +%Y%m%d)"
BUILD_ID="$SHA, $(date -u +%Y-%m-%d)"

# --- the jar -----------------------------------------------------------------
if [ -z "$JAR" ]; then
  [ -x "$PLUGIN_DIR/gradlew" ] || die "$PLUGIN_DIR/gradlew not found or not executable"
  mark="$(mktemp "${TMPDIR:-/tmp}/tp-mark.XXXXXX")"
  trap 'rm -f "$mark"' EXIT
  args=(shadowJar)
  [ "$RUN_TESTS" -eq 1 ] || args+=(-x test)
  echo "==> ./gradlew ${args[*]}"
  (cd "$PLUGIN_DIR" && ./gradlew "${args[@]}")
  # Newest *-all.jar written by that build, not just any stale one.
  JAR=""
  for f in "$PLUGIN_DIR"/build/libs/*-all.jar; do
    [ -e "$f" ] || continue
    if [ -z "$JAR" ] || [ "$f" -nt "$JAR" ]; then JAR="$f"; fi
  done
  [ -n "$JAR" ] || die "shadowJar produced no build/libs/*-all.jar"
  [ "$JAR" -nt "$mark" ] || die "build/libs jar is older than this build: $JAR"
fi
[ -f "$JAR" ] || die "jar not found: $JAR"
case "$JAR" in *.jar) ;; *) die "not a .jar: $JAR" ;; esac

# --- render ------------------------------------------------------------------
JAR_NAME="$SLUG.jar"
FOLDER="$SLUG-test-$STAMP"
OUT_DIR="${OUT_DIR:-$PLUGIN_DIR/build/test-package}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/tp-stage.XXXXXX")"
trap 'rm -rf "$STAGE"; rm -f "${mark:-}"' EXIT
mkdir -p "$STAGE/$FOLDER"

render() { # render TEMPLATE DEST ; values go through awk -v-free ENVIRON so any characters are safe
  TP_NAME="$DISPLAY_NAME" TP_DESC="$DESCRIPTION" TP_BUILD="$BUILD_ID" TP_JAR="$JAR_NAME" \
  awk '
    function rep(s, key, val,   out, i) {
      out = ""
      while ((i = index(s, key)) > 0) { out = out substr(s, 1, i - 1) val; s = substr(s, i + length(key)) }
      return out s
    }
    {
      $0 = rep($0, "__DISPLAY_NAME__", ENVIRON["TP_NAME"])
      $0 = rep($0, "__DESCRIPTION__", ENVIRON["TP_DESC"])
      $0 = rep($0, "__BUILD_ID__", ENVIRON["TP_BUILD"])
      $0 = rep($0, "__JAR_NAME__", ENVIRON["TP_JAR"])
      print
    }' "$1" > "$2"
}

render "$TEMPLATE_DIR/START.bat.tmpl" "$STAGE/$FOLDER/START.bat"
render "$TEMPLATE_DIR/README.txt.tmpl" "$STAGE/$FOLDER/README.txt"
# Windows wants CRLF in .bat and Notepad-friendly .txt.
for f in START.bat README.txt; do
  sed 's/\r$//; s/$/\r/' "$STAGE/$FOLDER/$f" > "$STAGE/$FOLDER/$f.crlf"
  mv "$STAGE/$FOLDER/$f.crlf" "$STAGE/$FOLDER/$f"
done
cp "$JAR" "$STAGE/$FOLDER/$JAR_NAME"

# A leftover placeholder means the template and this script have drifted.
if grep -l '__[A-Z_]*__' "$STAGE/$FOLDER/START.bat" "$STAGE/$FOLDER/README.txt" >/dev/null 2>&1; then
  die "unreplaced placeholder left in the rendered files"
fi

ZIP="$OUT_DIR/$FOLDER.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -qr -X "$ZIP" "$FOLDER")

echo
echo "Built: $ZIP"
echo "  plugin : $DISPLAY_NAME ($BUILD_ID)"
echo "  size   : $(du -h "$ZIP" | cut -f1)"
echo "Send that zip. The friend unzips it and double-clicks START.bat (needs Java 11+)."
