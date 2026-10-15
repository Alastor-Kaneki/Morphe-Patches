#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="$ROOT/build/reddit-gallery-test"
mkdir -p "$OUTPUT"
javac -d "$OUTPUT" \
  "$ROOT/extensions/extension/src/main/java/dev/alastorkaneki/morphe/extension/reddit/RedditGalleryLink.java" \
  "$ROOT/tools/RedditGalleryLinkSelfTest.java"
java -cp "$OUTPUT" RedditGalleryLinkSelfTest
