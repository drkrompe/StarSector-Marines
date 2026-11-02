#!/usr/bin/env bash
# One call to playBattle's diagnosis server: play-call.sh <tool> <arguments as JSON>.
# Prints the tool's answer. Exit 1: the tool said no, on stderr. Exit 2: no server to ask.
# PLAY_BATTLE_PORT picks the port -PplayServer=<port> chose; the default is 47811.
set -u

tool="$1"
arguments="$2"
port="${PLAY_BATTLE_PORT:-47811}"
body='{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"'"$tool"'","arguments":'"$arguments"'}}'

# The server waits up to 5 s for a frame, so anything longer than that is not coming.
reply=$(curl --silent --noproxy '*' --max-time 15 -H 'Content-Type: application/json' \
    --data-binary "$body" "http://127.0.0.1:$port/mcp")
status=$?
if [ "$status" -ne 0 ]; then
    echo "no playBattle server answered on 127.0.0.1:$port (curl exit $status)." >&2
    echo "It is up only while a window opened with: gradlew playBattle -PplayServer" >&2
    exit 2
fi

# On Windows "python3" can be a store stub that only fails, so each is tried rather than looked up.
for python in python3 python; do
    if "$python" -c '' >/dev/null 2>&1; then
        printf '%s' "$reply" | PYTHONIOENCODING=utf-8 "$python" -I -c '
import json, sys
reply = json.load(sys.stdin)
if "error" in reply:
    sys.exit(reply["error"]["message"])
result = reply["result"]
text = result["content"][0]["text"]
if result.get("isError"):
    sys.exit(text)
print(text)
'
        exit $?
    fi
done

# No Python: the answer is still in there, wrapped in its JSON-RPC envelope.
printf '%s\n' "$reply"
