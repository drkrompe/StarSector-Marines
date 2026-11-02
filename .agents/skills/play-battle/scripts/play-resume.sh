#!/usr/bin/env bash
# Resumes the battle: play-resume.sh [1|2|4]. With no speed, at the one it last ran at. Ends any run-to.
if [ "$#" -gt 1 ]; then
    echo 'usage: play-resume.sh [1|2|4]' >&2
    exit 64
fi
exec "$(dirname "$0")/play-call.sh" battle_resume '{"speed":"'"${1:-}"'"}'
