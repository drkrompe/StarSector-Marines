"""Generate one image with Codex CLI's built-in image_gen, from a Claude agent or a shell.

python tools/codex_imagegen.py --prompt PROMPT.txt --out OUT.png [--ref PATH=ROLE ...]
python tools/codex_imagegen.py --prompt PROMPT.txt --out OUT.png --adopt-thread THREAD_ID

Paid and explicit: one call is one generation, and nothing here retries. The prompt
is fed on stdin, which `codex exec` reads to EOF whenever stdin is not a terminal; a
prompt passed as an argument with stdin left open hangs it silently for as long as
the caller waits. The run happens in an empty scratch directory with a read-only
sandbox, so the agent cannot touch the repository. The image is found by the run's
own thread id under ~/.codex/generated_images/<thread>/, never by "newest file",
because other Codex sessions write there too. The lossless master is copied to
--out and a provenance record (prompt, references and roles, hashes, dimensions,
thread, timing) is written beside it as <out>.provenance.json.

--adopt-thread records provenance for an image an earlier run already produced,
without generating again. Exit status: 0 written, 1 generation failed, 2 usage.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

HEADER = ("Use the image_gen tool to generate exactly one image, then stop. "
          "Do not write files, run commands or edit anything.\n\n")
IMAGES = Path.home() / ".codex" / "generated_images"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def describe(path):
    with Image.open(path) as im:
        return {"path": str(path), "sha256": sha(path), "dimensions": list(im.size), "mode": im.mode}


def compose_prompt(body, refs):
    lines = [HEADER]
    if refs:
        lines.append("Attached reference images, in order:\n")
        lines += ["Image %d: %s\n" % (i + 1, role) for i, (_, role) in enumerate(refs)]
        lines.append("\n")
    lines.append(body.strip() + "\n")
    return "".join(lines)


def run_codex(prompt, refs, timeout):
    cmd = ["codex", "exec", "--skip-git-repo-check", "-s", "read-only", "--json"]
    for path, _ in refs:
        cmd += ["-i", str(Path(path).resolve())]
    cmd.append("-")
    with tempfile.TemporaryDirectory(prefix="codex-imagegen-") as scratch:
        try:
            done = subprocess.run(cmd, input=prompt, text=True, encoding="utf-8", capture_output=True,
                                  cwd=scratch, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise SystemExit("codex exec exceeded %ds; no retry made." % timeout)
    events, messages, usage, thread = [], [], None, None
    for line in done.stdout.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        events.append(event)
        if event.get("type") == "thread.started":
            thread = event.get("thread_id")
        item = event.get("item") or {}
        if item.get("type") == "agent_message" and item.get("text"):
            messages.append(item["text"])
        if event.get("type") == "turn.completed":
            usage = event.get("usage")
    if done.returncode != 0 or not thread:
        sys.stderr.write(done.stderr[-2000:])
        raise SystemExit("codex exec failed (exit %d); no retry made." % done.returncode)
    return thread, messages, usage


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--prompt", required=True, type=Path, help="prompt text file (the brief, without tool instructions)")
    parser.add_argument("--out", required=True, type=Path, help="lossless PNG master to write")
    parser.add_argument("--ref", action="append", default=[], metavar="PATH=ROLE",
                        help="reference image and its explicit role (repeatable, attached in order)")
    parser.add_argument("--timeout", type=int, default=420, help="seconds before giving up (default 420)")
    parser.add_argument("--adopt-thread", help="record an image an earlier run already generated")
    parser.add_argument("--replace", action="store_true", help="allow overwriting an existing --out")
    args = parser.parse_args()
    refs = []
    for spec in args.ref:
        path, sep, role = spec.partition("=")
        if not sep or not role.strip() or not Path(path).is_file():
            parser.error("--ref needs an existing PATH=ROLE: " + spec)
        refs.append((Path(path), role.strip()))
    if args.out.exists() and not args.replace:
        parser.error("%s exists; pass --replace to overwrite it" % args.out)
    body = args.prompt.read_text(encoding="utf-8")
    prompt = compose_prompt(body, refs)
    started = time.time()
    if args.adopt_thread:
        thread, messages, usage = args.adopt_thread, [], None
    else:
        print("Generating with Codex image_gen (paid, no retry)...", flush=True)
        thread, messages, usage = run_codex(prompt, refs, args.timeout)
    found = sorted((IMAGES / thread).glob("*.png"), key=lambda p: p.stat().st_mtime) if (IMAGES / thread).is_dir() else []
    if not found:
        print("\n".join(messages), file=sys.stderr)
        raise SystemExit("No image in %s; the agent did not call image_gen." % (IMAGES / thread))
    if len(found) > 1:
        print("Note: thread produced %d images; keeping the last, others recorded." % len(found))
    source = found[-1]
    args.out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, args.out)
    version = subprocess.run(["codex", "--version"], capture_output=True, text=True).stdout.strip()
    record = {
        "generator": "Codex CLI built-in image_gen",
        "codexVersion": version,
        "thread": thread,
        "source": str(source),
        "otherImagesInThread": [str(p) for p in found[:-1]],
        "output": describe(args.out),
        "promptFile": str(args.prompt),
        "promptSha256": sha(args.prompt),
        "promptAsSent": prompt if not args.adopt_thread else None,
        "adopted": bool(args.adopt_thread),
        "references": [dict(describe(p), role=r) for p, r in refs],
        "agentMessages": messages,
        "usage": usage,
        "elapsedSeconds": round(time.time() - started, 1),
    }
    provenance = args.out.with_name(args.out.name + ".provenance.json")
    provenance.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    out = record["output"]
    print("Wrote %s %dx%d %s (%.0fs); provenance %s" % (args.out, *out["dimensions"], out["mode"],
                                                        record["elapsedSeconds"], provenance.name))


if __name__ == "__main__":
    main()
