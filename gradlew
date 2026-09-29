#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VER=9.6.0
SHA256=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/aquawiz-gradle-$VER"
DIST="$CACHE/gradle-$VER"
ZIP="$CACHE/gradle-$VER-bin.zip"

verify_zip() {
  actual=$(sha256sum "$ZIP" | awk '{print $1}')
  if [ "$actual" != "$SHA256" ]; then
    echo "Gradle archive checksum mismatch." >&2
    echo "Expected: $SHA256" >&2
    echo "Actual:   $actual" >&2
    rm -f "$ZIP"
    exit 1
  fi
}

if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $VER..." >&2
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$VER-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VER-bin.zip"
    else
      echo "curl or wget is required for the first build" >&2
      exit 1
    fi
  fi
  verify_zip
  (cd "$CACHE" && unzip -q -o "$ZIP")
fi

exec "$DIST/bin/gradle" -p "$APP_HOME" "$@"
