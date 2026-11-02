#!/usr/bin/env bash
# Pauses the battle with its own pause button, and ends any run-to. Answers the tick it stopped on.
exec "$(dirname "$0")/play-call.sh" battle_pause '{}'
