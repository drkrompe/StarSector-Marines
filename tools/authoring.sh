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
# only Gradle can resolve.
#
# WHY THIS BUILDS ON EVERY CALL
#
# The generated launcher runs `java -classpath build/classes/java/test`, so it
# runs whatever was compiled last, with nothing in its output to say how old
# that is. Several of these tools rewrite hand-authored documents, and this has
# already destroyed authored work once: a call made straight after merging a new
# safety guard ran pre-guard classes, the guard did not fire, and a hand-cut
# tileset went with it. That failure is silent, looks exactly like a bug in the
# tool, and is worst precisely when it matters most — right after a change.
#
# So the freshness build is unconditional rather than a staleness heuristic. A
# hand-rolled mtime comparison is the wrong trade twice over: it cannot see
# deleted sources, changed resources, a changed dependency or an edited
# build.gradle, and every case it gets wrong is silently the original bug again.
# Gradle already knows the answer and answers it in about 0.7s against a warm
# daemon, against 0.14s for the launcher alone. Correctness beats latency for a
# tool that writes files.
#
# There is deliberately no bypass switch here. Calling
# build/authoring/authoring.sh directly still skips the check — that is a
# visibly different command, which is the point.
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
LAUNCHER="$ROOT/build/authoring/authoring.sh"

if [ ! -f "$LAUNCHER" ]; then
    # Only on a cold checkout, where the build is minutes rather than a second
    # and silence would look like a hang.
    echo "building the authoring tools for the first time in this checkout..." >&2
fi

# Output to stderr: stdout belongs to the tool result, so that a caller can pipe
# this straight into jq without build chatter landing in the JSON.
if ! (cd "$ROOT" && ./gradlew.bat installAuthoringTools --quiet >&2); then
    echo "authoring tools: the build failed, so the compiled classes are of unknown" >&2
    echo "age. Refusing to call a tool with them - fix the build and try again." >&2
    exit 3
fi

exec sh "$LAUNCHER" "$@"
