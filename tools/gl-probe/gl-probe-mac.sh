#!/bin/bash
# Can this Mac give the headless snapshot drain an LWJGL 2 context? Run it in Terminal on the Mac's own desktop
# (an SSH shell has no window-server session, and LWJGL 2 crashes there before it tries). See `gpu-review-drain.md`.
# Usage: tools/gl-probe/gl-probe-mac.sh [pbuffer|display]
set -e
APP=/Applications/Starsector.app/Contents
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/native"
# Java 17+ asks for liblwjgl.dylib; the game ships the same library as .jnilib.
ln -s "$APP/Resources/Java/native/macosx/liblwjgl.jnilib" "$WORK/native/liblwjgl.dylib"
javac --release 17 -d "$WORK" -cp "$APP/Resources/Java/lwjgl.jar" "$(dirname "$0")/GlProbe.java"
# The game's own JRE: x86_64 under Rosetta, the only kind of JVM that can load that library.
"$APP/Home/bin/java" -Dorg.lwjgl.librarypath="$WORK/native" -cp "$WORK:$APP/Resources/Java/lwjgl.jar" GlProbe "${1:-pbuffer}"
