#!/usr/bin/env bash
# Asks the running battle: play-query.sh "why 4" "units squad=4". Commands given together are answered at one tick.
set -u

if [ "$#" -eq 0 ]; then
    echo 'usage: play-query.sh "<command>" ["<command>" ...], such as "squads" or "why 4"' >&2
    exit 64
fi

commands=""
for command in "$@"; do
    command=${command//\\/\\\\}
    command=${command//\"/\\\"}
    commands="$commands$command\\n"
done
exec "$(dirname "$0")/play-call.sh" battle_query '{"commands":"'"$commands"'"}'
