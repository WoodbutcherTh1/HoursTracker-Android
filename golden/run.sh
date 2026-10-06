#!/usr/bin/env bash
# Generates the golden files from a local checkout of the iOS app.
#
# Usage: golden/run.sh /path/to/iOS-repo
#
# Requires macOS with the Swift toolchain (Xcode or Command Line Tools).
# Refuses to run on a dirty iOS checkout, because the commit SHA recorded in
# manifest.json must describe exactly the code that produced the data.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: golden/run.sh /path/to/iOS-repo" >&2
  exit 64
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "error: run this on a Mac. Foundation on Linux behaves differently from Apple's." >&2
  exit 1
fi

if ! command -v swift >/dev/null 2>&1; then
  echo "error: 'swift' not found. Install Xcode or the Command Line Tools (xcode-select --install)." >&2
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
IOS_DIR="$(cd "$1" && pwd)"
HARNESS="$HERE/harness"
LINK_DIR="$HARNESS/Sources/iOSSources"
OUT_DIR="$HERE/data"

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

cleanup() {
  rm -rf "$LINK_DIR"
}
trap cleanup EXIT

# Link the iOS files listed in ios-sources.txt (comments and blank lines are skipped).
rm -rf "$LINK_DIR"
LINKED=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|'#'*) continue ;; esac
  if [ ! -f "$IOS_DIR/$line" ]; then
    echo "error: listed file not found in the iOS repo: $line" >&2
    exit 1
  fi
  mkdir -p "$LINK_DIR"
  target="$LINK_DIR/$(basename "$line")"
  if [ -e "$target" ]; then
    echo "error: two listed files share the name $(basename "$line")." >&2
    exit 1
  fi
  ln -s "$IOS_DIR/$line" "$target"
  LINKED=$((LINKED + 1))
done < "$HARNESS/ios-sources.txt"
echo "Linked $LINKED iOS source file(s)."

swift run --package-path "$HARNESS" -c release GoldenGen "$OUT_DIR" "$IOS_SHA"

echo
echo "Done. Review the result, then commit it:"
echo "  git add golden/data"
echo "  git commit -m \"test(golden): regenerate from iOS ${IOS_SHA:0:7}\""
