#!/bin/sh
# The one rule for finding Blender, shared by every art-source build script that
# launches it (the Python twin is blender_path.py beside this file):
#   $BLENDER if set, else the platform default -- Windows: Blender 5.2 under
#   Program Files; macOS: /Applications/Blender.app; anything else: `blender` on PATH.
#
# Sourced (`. path/to/blender.sh`), it sets BLENDER and changes nothing else.
# Run (`sh path/to/blender.sh --background ...`), it runs Blender with those
# arguments -- the invocation the READMEs show, since `sh` is on PATH in Git Bash,
# PowerShell (via Git for Windows) and macOS alike.
if [ -z "${BLENDER:-}" ]; then
  case "$(uname -s)" in
    Darwin) BLENDER=/Applications/Blender.app/Contents/MacOS/Blender ;;
    MINGW*|MSYS*|CYGWIN*) BLENDER="/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" ;;
    *) BLENDER=blender ;;
  esac
fi
# The harnesses that source this also run plain Python between Blender steps, and the
# Mac has only `python3` where Windows has only `python`: $PYTHON if set, else the first
# of the two that actually runs (so the Windows Store's python3 stub is passed over).
if [ -z "${PYTHON:-}" ]; then
  if python3 -c '' >/dev/null 2>&1; then PYTHON=python3; else PYTHON=python; fi
fi
case "$0" in
  *blender.sh) exec "$BLENDER" "$@" ;;
esac
