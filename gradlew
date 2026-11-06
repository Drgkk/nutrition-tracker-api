#!/usr/bin/env bash
# Lightweight Gradle bootstrapper for this demo. The distribution version is
# read from gradle/wrapper/gradle-wrapper.properties and downloaded on first use.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties"
GRADLE_VERSION="$(sed -n 's#^distributionUrl=.*gradle-\([0-9][0-9.]*\)-bin\.zip$#\1#p' "$PROPERTIES_FILE")"
[[ -n "$GRADLE_VERSION" ]] || { echo "Cannot read Gradle version from $PROPERTIES_FILE" >&2; exit 1; }
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_USER_HOME/bootstrap/gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  command -v curl >/dev/null 2>&1 || { echo 'curl is required to bootstrap Gradle.' >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo 'unzip is required to bootstrap Gradle.' >&2; exit 1; }
  mkdir -p "$GRADLE_USER_HOME/bootstrap"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl --fail --location --silent --show-error \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    --output "$TMP_DIR/gradle.zip"
  unzip -q "$TMP_DIR/gradle.zip" -d "$TMP_DIR"
  mv "$TMP_DIR/gradle-$GRADLE_VERSION" "$INSTALL_DIR"
fi

exec "$GRADLE_BIN" "$@"
