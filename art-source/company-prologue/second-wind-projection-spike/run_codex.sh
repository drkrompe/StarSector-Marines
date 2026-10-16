#!/usr/bin/env bash
# Drive Codex's built-in image_gen once from the CLI and keep the result.
#   ./run_codex.sh <prompt.txt> <out.png> <input1.png> [input2.png ...]
# The wrapper text forbids prompt rewriting and extra generations; Codex's
# agent otherwise paraphrases the prompt before calling the tool.
set -euo pipefail
CODEX=${CODEX:-/c/Users/Dillon/AppData/Local/Programs/OpenAI/Codex/bin/codex}
prompt=$1; out=$2; shift 2
images=(); for f in "$@"; do images+=(-i "$f"); done
outwin=$(cygpath -w "$(realpath -m "$out")")
mkdir -p build; log="build/$(basename "${out%.png}").codex-log.txt"
start=$(date +%s)
{
  echo "Call your built-in image generation tool EXACTLY ONCE. Attached image 1 is INPUT 1 and must be passed to the tool as the image to edit / reference; any further attached images are passed as additional references in order (INPUT 2, ...)."
  echo "Pass the prompt between the markers to the tool VERBATIM. Do not rewrite, summarise or extend it. Do not generate any other image and do not retry."
  echo "After it returns, copy the generated PNG to: $outwin"
  echo "Then print the absolute path of the original generated file on one line starting SOURCE: and stop. Do not read or open any other file, skill or project document; do nothing else."
  echo "----- PROMPT START -----"
  cat "$prompt"
  echo "----- PROMPT END -----"
} | "$CODEX" exec --skip-git-repo-check -s workspace-write "${images[@]}" - 2>&1 | tee "$log"
echo "ELAPSED_SECONDS=$(( $(date +%s) - start ))" | tee -a "$log"
