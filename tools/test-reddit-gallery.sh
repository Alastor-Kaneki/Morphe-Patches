#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="$ROOT/build/reddit-gallery-test"
mkdir -p "$OUTPUT"
javac -d "$OUTPUT" \
  "$ROOT/extensions/extension/src/main/java/dev/alastorkaneki/morphe/extension/reddit/RedditGalleryLink.java" \
  "$ROOT/extensions/extension/src/main/java/dev/alastorkaneki/morphe/extension/reddit/RedditPostToken.java" \
  "$ROOT/tools/RedditGalleryLinkSelfTest.java" \
  "$ROOT/tools/RedditNativePostSelfTest.java" \
  "$ROOT/extensions/extension/src/main/java/dev/alastorkaneki/morphe/extension/reddit/RedditShareLink.java" \
  "$ROOT/tools/RedditShareLinkSelfTest.java" \
  "$ROOT/extensions/extension/src/main/java/dev/alastorkaneki/morphe/extension/reddit/RedditNativePostId.java" \
  "$ROOT/tools/RedditNativeOverflow202641SelfTest.java"
java -cp "$OUTPUT" RedditGalleryLinkSelfTest
java -cp "$OUTPUT" RedditNativePostSelfTest
java -cp "$OUTPUT" RedditShareLinkSelfTest
java -cp "$OUTPUT" RedditNativeOverflow202641SelfTest
