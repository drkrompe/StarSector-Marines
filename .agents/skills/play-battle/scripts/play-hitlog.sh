#!/usr/bin/env bash
# The server's one write: play-hitlog.sh on|off|clear|status. Records hits for "hits" and "hits-by"; changes nothing in the battle.
if [ "$#" -ne 1 ]; then
    echo 'usage: play-hitlog.sh on|off|clear|status' >&2
    exit 64
fi
exec "$(dirname "$0")/play-call.sh" battle_hitlog '{"action":"'"$1"'"}'
