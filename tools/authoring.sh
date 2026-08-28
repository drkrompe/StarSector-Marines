#!/bin/sh
# Call one authoring tool and exit.
#
#   tools/authoring.sh --list
#   tools/authoring.sh tileset_list
#   tools/authoring.sh tileset_measure '{"sheet":"urban-tileset","gridCols":10,"gridRows":10}'
#
# This is the shell front door onto the same tools the MCP server serves. It
# exists because an MCP stdio server has to be registered before the session
# that wants it starts, and a session that discovers mid-task that it needs to
# look at a tileset cannot go back and do that.
#
# The real launcher is generated, because it embeds an absolute classpath that
# only Gradle can resolve. This wrapper generates it on first use so that
# calling a tool never requires a setup step first. Re-run
# `gradlew.bat installAuthoringTools` yourself after a dependency change.
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
LAUNCHER="$ROOT/build/authoring/authoring.sh"

if [ ! -f "$LAUNCHER" ]; then
    echo "installing the authoring launchers (one-time)..." >&2
    # Output to stderr: stdout belongs to the tool result, so that a caller can
    # pipe this straight into jq without build chatter landing in the JSON.
    (cd "$ROOT" && ./gradlew.bat installAuthoringTools --quiet >&2)
fi

exec sh "$LAUNCHER" "$@"
