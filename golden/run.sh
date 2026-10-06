#!/usr/bin/env bash
# Generates the golden files from a local checkout of the iOS app.
#
# Usage: golden/run.sh [--provisional] /path/to/iOS-repo
#
# Normal mode needs macOS with the Swift toolchain (Xcode or Command Line Tools) and
# writes golden/data. It refuses to run on a dirty iOS checkout, because the commit SHA
# recorded in manifest.json must describe exactly the code that produced the data.
#
# --provisional works on any OS (including Linux) and writes golden/provisional, which
# is ignored by git. Provisional data is NOT authoritative: Foundation on Linux behaves
# differently from Apple's. It exists only to check that the harness builds and to develop
# the Kotlin code before the real data is available.
set -euo pipefail

PROVISIONAL=0
if [ "${1:-}" = "--provisional" ]; then
  PROVISIONAL=1
  shift
fi

if [ "$#" -ne 1 ]; then
  echo "usage: golden/run.sh [--provisional] /path/to/iOS-repo" >&2
  exit 64
fi

OS="$(uname -s)"
if [ "$PROVISIONAL" -eq 0 ] && [ "$OS" != "Darwin" ]; then
  echo "error: run this on a Mac. Foundation on Linux behaves differently from Apple's." >&2
  echo "       (--provisional runs anywhere, but its output is not authoritative.)" >&2
  exit 1
fi

if ! command -v swift >/dev/null 2>&1; then
  echo "error: 'swift' not found. Install Xcode or the Command Line Tools (xcode-select --install)." >&2
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
IOS_DIR="$(cd "$1" && pwd)"
HARNESS="$HERE/harness"
LINK_DIR="$HARNESS/Sources/GoldenGen/iOS"
if [ "$PROVISIONAL" -eq 1 ]; then
  OUT_DIR="$HERE/provisional"
else
  OUT_DIR="$HERE/data"
fi

if ! git -C "$IOS_DIR" rev-parse --git-dir >/dev/null 2>&1; then
  echo "error: $IOS_DIR is not a git repository." >&2
  exit 1
fi

if [ -n "$(git -C "$IOS_DIR" status --porcelain)" ]; then
  echo "error: the iOS checkout has uncommitted changes. Commit or stash them first." >&2
  exit 1
fi

IOS_SHA="$(git -C "$IOS_DIR" rev-parse HEAD)"
echo "iOS commit: $IOS_SHA"
if [ "$PROVISIONAL" -eq 1 ]; then
  echo "PROVISIONAL run on $OS: output goes to golden/provisional and must never be committed."
fi

cleanup() {
  rm -rf "$LINK_DIR"
}
trap cleanup EXIT

# Link the iOS files listed in ios-sources.txt (comments and blank lines are skipped).
rm -rf "$LINK_DIR"
mkdir -p "$LINK_DIR"
LINKED=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|'#'*) continue ;; esac
  if [ ! -f "$IOS_DIR/$line" ]; then
    echo "error: listed file not found in the iOS repo: $line" >&2
    exit 1
  fi
  target="$LINK_DIR/$(basename "$line")"
  if [ -e "$target" ]; then
    echo "error: two listed files share the name $(basename "$line")." >&2
    exit 1
  fi
  ln -s "$IOS_DIR/$line" "$target"
  LINKED=$((LINKED + 1))
done < "$HARNESS/ios-sources.txt"
echo "Linked $LINKED iOS source file(s)."

swift build --package-path "$HARNESS" -c release
BIN="$(swift build --package-path "$HARNESS" -c release --show-bin-path)/GoldenGen"

# Start from an empty output folder so files of removed case groups never linger.
mkdir -p "$OUT_DIR"
find "$OUT_DIR" -maxdepth 1 -name '*.json' -delete

# One process per environment. The locale reaches the process the way each OS expects.
for env in il ru utc; do
  case "$env" in
    il)  LOCALE_ID=he_IL ;;
    ru)  LOCALE_ID=ru_RU ;;
    utc) LOCALE_ID=en_GB ;;
  esac
  echo
  echo "== environment: $env ($LOCALE_ID)"
  if [ "$OS" = "Darwin" ]; then
    "$BIN" --env "$env" --out "$OUT_DIR" -AppleLocale "$LOCALE_ID"
  else
    # The locale cannot be pinned outside macOS, so only the time zone is enforced here.
    LANG="$LOCALE_ID.UTF-8" LC_ALL="$LOCALE_ID.UTF-8" "$BIN" --env "$env" --out "$OUT_DIR" --lenient
  fi
done

echo
"$BIN" --manifest --out "$OUT_DIR" --ios-sha "$IOS_SHA"

echo
if [ "$PROVISIONAL" -eq 1 ]; then
  echo "Done (provisional). Do not commit golden/provisional."
else
  echo "Done. Review the result, then commit it:"
  echo "  git add golden/data"
  echo "  git commit -m \"test(golden): regenerate from iOS ${IOS_SHA:0:7}\""
fi
