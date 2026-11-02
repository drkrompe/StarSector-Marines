#!/usr/bin/env bash
# Runs the battle on and stops it, waiting until it has: play-run-to.sh 900 | +300 | beat=TEXT [speed=1|2|4]
# A tick and a beat together stop at whichever comes first. Prints play_status, whose "run" says how it ended.
# Exit 1: it did not start, or the player's hand or the screen ended it early.
set -u

usage='usage: play-run-to.sh <tick> | +<ticks> | beat=<text> [speed=1|2|4]'
scripts=$(dirname "$0")
arguments=""
add() { arguments="$arguments${arguments:+,}$1"; }

for word in "$@"; do
    case "$word" in
        beat=*) add '"beat":"'"${word#beat=}"'"' ;;
        speed=*) add '"speed":"'"${word#speed=}"'"' ;;
        +*[!0-9]*) echo "$usage" >&2; exit 64 ;;
        +*) add '"by":'"${word#+}" ;;
        *[!0-9]*) echo "$usage" >&2; exit 64 ;;
        *) add '"tick":'"$word" ;;
    esac
done
if [ -z "$arguments" ]; then
    echo "$usage" >&2
    exit 64
fi

"$scripts/play-call.sh" battle_run_to "{$arguments}" >/dev/null || exit $?
while status=$("$scripts/play-call.sh" play_status '{}'); do
    case "$status" in
        *'"state":"RUNNING"'*) sleep 0.25 ;;
        *'"state":"CANCELLED"'*) printf '%s\n' "$status"; exit 1 ;;
        *) printf '%s\n' "$status"; exit 0 ;;
    esac
done
exit 2
