#!/usr/bin/env python3
"""Render queue shared by the Windows workstation and the MacBook Pro.

The queue lives on Google Drive (``starsector/render-queue/``). Every file in it
has exactly one writer, so Drive never sees two machines editing one file:

    config.json              render coordinator only: gates, holds, priority overrides
    jobs/<id>.json           the submitting session, written once, never edited
    inputs/<id>.bundle       the submitting session (git bundle of an unpushed commit)
    cancel/<id>              the submitter or the render coordinator
    claims/<id>.<machine>    that machine's runner (``any`` jobs only)
    done/<id>.<machine>      that machine's runner: the job's result
    status/<machine>.json    that machine's runner: heartbeat, tags, current job, ETA
    log/<machine>.log        that machine's runner; log/jobs/<id>.<machine>.log per job
    out/<id>/<machine>/      default output copy destination

Each machine runs exactly one queue job at a time. A runner is installed to a
local folder (never run from Drive), polls the queue, and launches each job
through its own wrapper process, which records the exit status, so a runner
restart neither loses nor orphans a job. Swapping a job out (for a review job,
or because the gate closed) waits for the frame in progress to land, then stops
the job cleanly; it is relaunched later and its render skips finished frames.

Commands (``python tools/render_queue.py <command> -h`` for options):

    submit  JOB.json [--commit C]        validate and publish a job
    split   TEMPLATE.json --frames A:B   a frame range as parts either machine takes, + assembly
    status  [--job ID] [--json]          per machine: gate, running, ETA, queued
    cancel  ID                           withdraw a job (submitter or coordinator)
    gate    MACHINE run|pause            render coordinator, on the owner's word
    hold / release ID                    render coordinator: keep a job back
    prioritize ID N                      render coordinator: override priority
    install                              install the runner on this machine
    runner                               the polling loop (started by install)
    wrap    RUN_DIR                      internal: one job's process wrapper

Python 3.9 compatible (the Mac's /usr/bin/python3); standard library only.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import fnmatch
import getpass
import glob
import hashlib
import json
import os
import re
import shutil
import signal
import socket
import statistics
import subprocess
import sys
import time
import traceback
from pathlib import Path

VERSION = "2026-10-23.2"
MACHINES = ("mac", "windows")
LANES = ("light", "review", "final")
DEFAULT_CONFIG = {
    # The manual gate. Changed only by the render coordinator, on the owner's word.
    "gates": {"windows": "pause", "mac": "pause"},
    # Lanes in running order; a list that leaves a lane out is ignored. Lanes in
    # ungated_lanes run whatever the gate says (owner rule: half size, at most 16
    # samples, or a low-fps low-res clip).
    "lanes": ["light", "review", "final"],
    "ungated_lanes": ["light"],
    # A job in an earlier lane swaps a running multi-frame job out at its next
    # frame boundary (never mid-frame), then back in.
    "preempt": True,
    # A preempted job has rendered at least this long since its last launch before
    # an earlier-lane job may swap it out again, so a stream of light jobs cannot
    # starve a final with scene rebuilds. Light jobs wait at most this long.
    "preempt_min_run_seconds": 1200,
    # Pause and hold also wait for the frame in progress to land, but only this
    # long before stopping mid-frame. Jobs without a frames check stop at once.
    "swap_at_frame_boundary": True,
    "swap_wait_max_seconds": 420,
    # Mac swap-out: "stop" (clean stop, relaunch with frame skip) or "freeze"
    # (SIGSTOP; keeps the swapped-out Blender in memory beside the new one).
    "mac_swap": "stop",
    # Run jobs at low CPU priority (Windows BELOW_NORMAL, Mac nice 10) so the
    # machine stays usable. A job may set "low_priority": false.
    "low_priority": True,
    # A light job running longer than this is stopped and failed.
    "light_max_seconds": 1800,
    # How long a finished job's checkout and scratch stay on disk before the idle
    # sweep removes them. A copy into the Drive folder only means Drive accepted it
    # locally, not that it uploaded, so an ok job keeps its local copy a while; a
    # failed or cancelled job keeps it longer for a look or a hand copy. A job whose
    # output copy failed is never swept: it is logged and left for a person.
    "retain_ok_hours": 12,
    "retain_failed_hours": 48,
    # A busy machine leaves a fresh `any` job this long for an idle one to claim.
    "any_grace_seconds": 120,
    # Daytime routing, switched by the render coordinator (`prefer mac` / `prefer off`):
    # while set, the other machine leaves an unclaimed `any` job in these lanes to
    # the preferred one until the job has waited wait_seconds, unless the preferred
    # runner is down or its gate is paused. Windows is the owner's daily machine;
    # the Mac is the render box. None (the night setting) routes as before.
    "prefer": None,  # e.g. {"machine": "mac", "wait_seconds": 1800, "lanes": ["review", "final"]}
    # Do not start a job while a background Blender this runner did not start is
    # running (a hand-started render); frozen (stopped) ones do not count.
    "block_on_foreign_blender": True,
    # Render coordinator's queue controls: job ids held back, priority overrides.
    "held": [],
    "priority_overrides": {},
    "poll_seconds": 20,
    # How long an `any` claim waits for Drive to sync the other machine's claim.
    "claim_wait_seconds": 90,
    # Reserved for later (a night window or idle rule); not read yet.
    "schedule": None,
}
IS_WINDOWS = os.name == "nt"
ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,80}$")
HUB_SSH = "C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local -o ConnectTimeout=10"


# ---------------------------------------------------------------- utilities

def now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def this_machine() -> str:
    return "windows" if IS_WINDOWS else "mac"


def local_root() -> Path:
    if IS_WINDOWS:
        return Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "starsector-render-queue"
    return Path.home() / "render-queue"


def find_drive_root() -> Path:
    env = os.environ.get("RENDER_QUEUE_DRIVE")
    if env:
        return Path(env)
    if IS_WINDOWS:
        return Path("G:/My Drive/starsector")
    hits = sorted(glob.glob(str(Path.home() / "Library/CloudStorage/GoogleDrive-*/My Drive/starsector")))
    if not hits:
        raise SystemExit("Google Drive starsector folder not found under ~/Library/CloudStorage")
    return Path(hits[0])


def read_json(path: Path, default=None):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default
    except (OSError, ValueError):
        # A file Drive is still materialising reads as partial: treat as absent.
        return default


def write_json(path: Path, data) -> None:
    """Write via a sibling temp file and replace, so a reader never sees half a file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name("." + path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")
    for attempt in range(5):
        try:
            os.replace(tmp, path)
            return
        except PermissionError:
            time.sleep(0.5 * (attempt + 1))
    os.replace(tmp, path)


def write_once(path: Path, data) -> None:
    if path.exists():
        raise SystemExit(f"{path} already exists; queue files are written once")
    write_json(path, data)


def allow_dataless_reads() -> None:
    """macOS refuses to download a Drive file that is not yet local ("dataless")
    for a background process: reads fail with EDEADLK, "Resource deadlock
    avoided". A LaunchAgent is background, so opt this process in."""
    if IS_WINDOWS or sys.platform != "darwin":
        return
    try:
        import ctypes
        libc = ctypes.CDLL("libc.dylib", use_errno=True)
        # IOPOL_TYPE_VFS_MATERIALIZE_DATALESS_FILES, IOPOL_SCOPE_PROCESS, IOPOL_MATERIALIZE_DATALESS_FILES_ON
        libc.setiopolicy_np(3, 0, 2)
    except (OSError, AttributeError):
        pass


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd, cwd=None, check=True, timeout=None, env=None) -> subprocess.CompletedProcess:
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = 0x08000000  # CREATE_NO_WINDOW
    return subprocess.run([str(c) for c in cmd], cwd=cwd, check=check, timeout=timeout, env=env,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          universal_newlines=True, **kw)


def git_env() -> dict:
    env = dict(os.environ)
    if IS_WINDOWS:
        env["GIT_SSH_COMMAND"] = HUB_SSH
    return env


def fmt_secs(s) -> str:
    if s is None:
        return "?"
    s = int(s)
    return f"{s // 3600}h{s % 3600 // 60:02d}m" if s >= 3600 else f"{s // 60}m{s % 60:02d}s"


class Queue:
    def __init__(self, root: Path):
        self.root = root
        self.unreadable: set = set()

    def d(self, name: str) -> Path:
        p = self.root / name
        p.mkdir(parents=True, exist_ok=True)
        return p

    def config(self) -> dict:
        cfg = json.loads(json.dumps(DEFAULT_CONFIG))
        cfg.update(read_json(self.root / "config.json", {}) or {})
        return cfg

    def jobs(self) -> dict:
        out = {}
        for p in sorted(self.d("jobs").glob("*.json")):
            job = read_json(p)
            if job is None and p.stat().st_size > 0:
                self.unreadable.add(p.name)
            if isinstance(job, dict) and job.get("id") == p.stem:
                out[p.stem] = job
        return out

    def done(self) -> dict:
        """{job_id: {machine: result}}"""
        out: dict = {}
        for p in self.d("done").iterdir():
            if p.name.startswith("."):
                continue
            job_id, _, machine = p.name.rpartition(".")
            if machine in MACHINES:
                res = read_json(p)
                if not isinstance(res, dict):
                    # Present but unreadable (still syncing): a result exists, so
                    # never treat the job as unfinished and run it again.
                    res = {"result": "unreadable"}
                out.setdefault(job_id, {})[machine] = res
        return out

    def claims(self, job_id: str) -> dict:
        out = {}
        for m in MACHINES:
            c = read_json(self.d("claims") / f"{job_id}.{m}")
            if isinstance(c, dict):
                out[m] = c
        return out

    def statuses(self) -> dict:
        return {m: read_json(self.d("status") / f"{m}.json", {}) or {} for m in MACHINES}

    def cancelled(self, job_id: str) -> bool:
        return (self.d("cancel") / job_id).exists()

    def log(self, machine: str, msg: str) -> None:
        line = f"{now_iso()} {msg}\n"
        try:
            with open(self.d("log") / f"{machine}.log", "a", encoding="utf-8") as f:
                f.write(line)
        except OSError:
            pass


def job_succeeded(done_for_job: dict) -> bool:
    return any(r.get("result") == "ok" for r in done_for_job.values())


def claim_winner(claims: dict):
    """Earliest claim wins; claims stamped within a second fall to machine-name order."""
    if not claims:
        return None
    ordered = sorted(claims.items(), key=lambda kv: (round(float(kv[1].get("time", 0))), kv[0]))
    return ordered[0][0]


def job_priority(job: dict, cfg: dict) -> int:
    return int(cfg.get("priority_overrides", {}).get(job["id"], job.get("priority", 0)))


def lane_order(cfg: dict) -> list:
    lanes = cfg.get("lanes")
    return list(lanes) if isinstance(lanes, list) and set(lanes) >= set(LANES) else list(LANES)


def order_key(job: dict, cfg: dict, resumed: bool = False):
    lanes = lane_order(cfg)
    lane = lanes.index(job["lane"]) if job["lane"] in lanes else len(lanes)
    return (lane, 0 if resumed else 1, -job_priority(job, cfg), job.get("submitted", ""), job["id"])


def gate_allows(job: dict, cfg: dict, machine: str) -> bool:
    return cfg.get("gates", {}).get(machine) == "run" or job.get("lane") in cfg.get("ungated_lanes", [])


# ---------------------------------------------------------------- job schema

def validate_job(job: dict) -> list:
    errs = []
    jid = job.get("id")
    if not isinstance(jid, str) or not ID_RE.match(jid):
        errs.append("id: lowercase letters, digits, '.', '_' or '-', 3-81 chars")
    if job.get("machine") not in ("windows", "mac", "any"):
        errs.append("machine: windows | mac | any")
    if job.get("lane") not in LANES:
        errs.append("lane: " + " | ".join(LANES))
    if not isinstance(job.get("priority", 0), int):
        errs.append("priority: integer, higher runs first within a lane")
    if not isinstance(job.get("after", []), list):
        errs.append("after: list of job ids")
    if not isinstance(job.get("requires", []), list):
        errs.append("requires: list of capability tags")
    cmd = job.get("command")
    if isinstance(cmd, dict):
        if not all(isinstance(v, list) and v for v in cmd.values()) or not set(cmd) <= set(MACHINES):
            errs.append("command: per-machine form is {\"windows\": [...], \"mac\": [...]}")
    elif not (isinstance(cmd, list) and cmd and all(isinstance(a, str) for a in cmd)):
        errs.append("command: argv list of strings (placeholders allowed)")
    if not isinstance(job.get("env", {}), dict):
        errs.append("env: object")
    for i, inp in enumerate(job.get("inputs", [])):
        kind = inp.get("kind")
        if kind == "git":
            if not re.match(r"^[0-9a-f]{40}$", str(inp.get("commit", ""))):
                errs.append(f"inputs[{i}]: git input needs the full 40-character commit id")
        elif kind == "drive":
            if not inp.get("from") or not inp.get("to"):
                errs.append(f"inputs[{i}]: drive input needs from and to")
        else:
            errs.append(f"inputs[{i}]: kind is git | drive")
    if len([i for i in job.get("inputs", []) if i.get("kind") == "git"]) > 1:
        errs.append("inputs: at most one git input (it becomes {checkout})")
    fr = job.get("frames")
    if fr is not None and not (isinstance(fr, dict) and fr.get("dir") and isinstance(fr.get("count"), int)):
        errs.append("frames: {dir, glob, count}")
    for o in job.get("outputs", []):
        if not o.get("from"):
            errs.append("outputs: each needs from (and optional glob, to)")
        if str(o.get("from", "")).replace("\\", "/").startswith("{drive}"):
            errs.append("outputs: render to a local folder ({work}, {checkout}) and copy to Drive with 'to'")
    return errs


# ---------------------------------------------------------------- submit

def repo_root() -> Path:
    return Path(run(["git", "rev-parse", "--show-toplevel"]).stdout.strip())


def hub_reachable() -> bool:
    r = run(["git", "ls-remote", "--heads", "hub", "main"], check=False, timeout=30, env=git_env())
    return r.returncode == 0


def prepare_job(q: Queue, job: dict) -> None:
    """Fills a job's defaults and submission stamps; refuses an id already in the queue."""
    for k, v in (("priority", 0), ("after", []), ("inputs", []), ("env", {}), ("requires", [])):
        job.setdefault(k, v)
    job["submitted"] = now_iso()
    job["submitted_epoch"] = time.time()
    job["submitted_on"] = this_machine()
    job.setdefault("submitted_by", f"{getpass.getuser()}@{socket.gethostname()}")
    if (q.d("jobs") / f"{job.get('id')}.json").exists():
        raise SystemExit(f"job {job.get('id')} already exists; pick a new id")


def pin_commit(q: Queue, job: dict, rev: str, via: str, name: str) -> dict:
    """Pins `rev` as the job's git input and makes it reachable from the machine that will run it:
    nothing to do on origin/main or for a job pinned to this machine, else a hub ref or a bundle
    named after `name`. Returns the git input, so jobs sharing the commit can share its transport."""
    commit = run(["git", "rev-parse", rev + "^{commit}"]).stdout.strip()
    git_in = [i for i in job["inputs"] if i.get("kind") == "git"]
    if not git_in:
        git_in = [{"kind": "git"}]
        job["inputs"].insert(0, git_in[0])
    gi = git_in[0]
    gi["commit"] = commit
    run(["git", "fetch", "--quiet", "origin"], check=False, timeout=300)
    on_main = run(["git", "merge-base", "--is-ancestor", commit, "origin/main"], check=False).returncode == 0
    local_only = job.get("machine") == this_machine()
    if on_main:
        print(f"{commit[:9]} is on origin/main; every machine can check it out")
    elif local_only:
        print(f"{commit[:9]} runs on this machine, from this clone; nothing to transport")
    elif via == "hub":
        if not hub_reachable():
            raise SystemExit("hub is unreachable from here, so a mac/any job cannot carry an "
                             "unpushed commit; pin the job to this machine, or use --via bundle")
        ref = f"refs/render/{name}"
        run(["git", "push", "--quiet", "hub", f"{commit}:{ref}"], timeout=600, env=git_env())
        gi["hub_ref"] = ref
        print(f"pushed {commit[:9]} to hub as {ref}")
    else:
        ref = f"refs/render-queue/{name}"
        bundle = q.d("inputs") / f"{name}.bundle"
        run(["git", "update-ref", ref, commit])
        try:
            run(["git", "bundle", "create", str(bundle), ref, "^origin/main"])
        finally:
            run(["git", "update-ref", "-d", ref], check=False)
        gi["bundle"] = f"inputs/{bundle.name}"
        gi["bundle_ref"] = ref
        print(f"bundled {commit[:9]} ^origin/main -> {bundle} ({bundle.stat().st_size} bytes)")
    return gi


def check_job(q: Queue, job: dict) -> None:
    """Refuses an invalid job; warns when no machine advertises what it requires."""
    errs = validate_job(job)
    if errs:
        raise SystemExit(f"invalid job {job.get('id')}:\n  " + "\n  ".join(errs))
    tags = {m: set(st.get("tags", [])) for m, st in q.statuses().items() if st.get("tags")}
    need = set(job["requires"])
    fits = [m for m in MACHINES if job["machine"] in (m, "any") and need <= tags.get(m, need)]
    if not fits:
        print(f"warning: no machine advertises {sorted(need)} for placement {job['machine']}; "
              f"known tags: { {m: sorted(t) for m, t in tags.items()} }")


def publish_job(q: Queue, job: dict) -> None:
    write_once(q.d("jobs") / f"{job['id']}.json", job)
    print(f"submitted {job['id']} ({job['machine']}, {job['lane']}) -> {q.root / 'jobs'}")


def cmd_submit(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    job = read_json(Path(args.job))
    if not isinstance(job, dict):
        raise SystemExit(f"{args.job}: not a JSON object")
    prepare_job(q, job)
    if args.commit:
        pin_commit(q, job, args.commit, args.via, job.get("id"))
    check_job(q, job)
    publish_job(q, job)
    return 0


# ---------------------------------------------------------------- split

PART_TOKENS = ("part_first", "part_last", "part_count", "part_start_s", "part_end_s")


def frame_parts(first: int, end: int, size: int) -> list:
    """[(first, count)] covering frames first..end-1 in parts of `size`; the last may be shorter."""
    return [(f, min(size, end - f)) for f in range(first, end, size)]


def fill_part(obj, values: dict):
    """`obj` with every {part_*} token in its strings replaced; other placeholders are left for the runner."""
    if isinstance(obj, str):
        for k, v in values.items():
            obj = obj.replace("{" + k + "}", v)
        return obj
    if isinstance(obj, list):
        return [fill_part(v, values) for v in obj]
    if isinstance(obj, dict):
        return {k: fill_part(v, values) for k, v in obj.items()}
    return obj


def part_values(first: int, count: int, fps: float) -> dict:
    return {"part_first": str(first), "part_last": str(first + count - 1), "part_count": str(count),
            "part_start_s": "%.6f" % (first / fps), "part_end_s": "%.6f" % ((first + count) / fps)}


def split_jobs(template: dict, first: int, end: int, size: int, fps: float, assemble=None) -> list:
    """The part jobs for frames first..end-1, then the assembly (if any) waiting on all of them.

    A part's id is the template's id plus its first frame and length, so a part renders the same
    frames under the same name however the range is cut, and redoing a sub-range of a shot gives
    new ids only for the parts that are new."""
    if end <= first:
        raise SystemExit(f"--frames {first}:{end}: the end is exclusive and must be past the first frame")
    if size < 1:
        raise SystemExit("--part must be at least one frame")
    if not any("{" + t + "}" in json.dumps(template) for t in PART_TOKENS):
        raise SystemExit("the template uses no part placeholder ({" + "}, {".join(PART_TOKENS) + "}), "
                         "so every part would render the same frames")
    jobs = []
    for f, c in frame_parts(first, end, size):
        j = fill_part(template, part_values(f, c, fps))
        j["id"] = f"{template['id']}-f{f:05d}-{c}"
        j.setdefault("machine", "any")
        if isinstance(j.get("frames"), dict):
            j["frames"]["count"] = c
        jobs.append(j)
    if assemble is not None:
        a = dict(assemble)
        a["after"] = list(a.get("after", [])) + [j["id"] for j in jobs]
        jobs.append(a)
    return jobs


def cmd_split(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    template = read_json(Path(args.template))
    if not isinstance(template, dict) or not template.get("id"):
        raise SystemExit(f"{args.template}: not a job template with an id")
    try:
        first, end = (int(x) for x in args.frames.split(":"))
    except ValueError:
        raise SystemExit(f"--frames {args.frames}: FIRST:END, frame numbers, END exclusive")
    assemble = read_json(Path(args.assemble)) if args.assemble else None
    jobs = split_jobs(template, first, end, args.part, args.fps, assemble)
    for j in jobs:
        print(f"  {j['id']:<64} {j.get('machine', 'any'):<8} "
              + (f"{j['frames']['count']} frames" if isinstance(j.get("frames"), dict) else f"after {len(j['after'])}"))
    if args.dry_run:
        return 0
    for j in jobs:
        prepare_job(q, j)
    if args.commit:
        gi = pin_commit(q, jobs[0], args.commit, args.via, template["id"])
        for j in jobs[1:]:
            j["inputs"] = [i for i in j["inputs"] if i.get("kind") != "git"]
            j["inputs"].insert(0, dict(gi))
    for j in jobs:
        check_job(q, j)
    for j in jobs:
        publish_job(q, j)
    return 0


# ---------------------------------------------------------------- status

def queue_view(q: Queue, cfg: dict) -> dict:
    """Everything the render coordinator needs in one read."""
    jobs = q.jobs()
    done = q.done()
    statuses = q.statuses()
    active = {}
    for m, st in statuses.items():
        for slot in [st.get("current")] + list(st.get("suspended", [])):
            if slot:
                active[slot["id"]] = (m, slot)
    view = {"queue": str(q.root), "machines": {}, "either": [], "blocked": [], "recent": []}
    for m in MACHINES:
        st = statuses[m]
        hb = int(time.time() - st["updated_epoch"]) if st.get("updated_epoch") else None
        view["machines"][m] = {
            "gate": cfg["gates"].get(m), "runner": st.get("runner"), "heartbeat_s": hb,
            "tags": st.get("tags", []), "note": st.get("note"),
            "running": st.get("current"), "suspended": st.get("suspended", []), "queued": [],
        }
    pending = []
    for jid, job in jobs.items():
        if jid in done:
            fin = max(r.get("finished_epoch", 0) for r in done[jid].values())
            if time.time() - fin < 86400:
                view["recent"].append({"id": jid, "results": {m: r.get("result") for m, r in done[jid].items()},
                                       "reason": "; ".join(r.get("reason", "") for r in done[jid].values()),
                                       "finished": max(r.get("finished", "") for r in done[jid].values())})
            continue
        if q.cancelled(jid) or jid in active:
            continue
        pending.append(job)
    pending.sort(key=lambda j: order_key(j, cfg))
    for job in pending:
        jid = job["id"]
        row = {"id": jid, "lane": job["lane"], "priority": job_priority(job, cfg)}
        missing = [a for a in job.get("after", []) if not job_succeeded(done.get(a, {}))]
        if jid in cfg.get("held", []):
            row["why"] = "held by the render coordinator"
        elif missing:
            row["why"] = "waiting for " + ", ".join(missing)
        if "why" in row:
            row["machine"] = job["machine"]
            view["blocked"].append(row)
            continue
        target = job["machine"]
        if target == "any":
            target = claim_winner(q.claims(jid)) or "any"
        if target == "any":
            view["either"].append(row)
        else:
            if not gate_allows(job, cfg, target):
                row["why"] = "gate closed"
            view["machines"][target]["queued"].append(row)
    view["recent"].sort(key=lambda r: r["finished"])
    return view


def print_view(view: dict) -> None:
    print(f"queue: {view['queue']}")
    for m, mv in view["machines"].items():
        hb = "never seen" if mv["heartbeat_s"] is None else f"heartbeat {mv['heartbeat_s']}s ago"
        print(f"\n{m}: gate {mv['gate']}, {hb}" + (f", note: {mv['note']}" if mv.get("note") else ""))
        print(f"  tags: {', '.join(mv['tags']) or '-'}")
        r = mv["running"]
        if r:
            print(f"  running  {r['id']} [{r['lane']}] {r.get('progress') or ''} "
                  f"{'%.0fs/frame' % r['sec_per_frame'] if r.get('sec_per_frame') else ''} "
                  f"ETA {fmt_secs(r.get('eta_s')) if r.get('eta_s') is not None else '?'}"
                  + (f"  swapping out ({r['swap']})" if r.get("swap") else ""))
        else:
            print("  running  -")
        for s in mv["suspended"]:
            print(f"  swapped  {s['id']} [{s['lane']}] {s.get('progress') or ''} ({s.get('why')})")
        for row in mv["queued"]:
            print(f"  queued   {row['id']} [{row['lane']} p{row['priority']}]"
                  + (f" ({row['why']})" if row.get("why") else ""))
    if view["either"]:
        print("\neither machine (unclaimed any):")
        for row in view["either"]:
            print(f"  queued   {row['id']} [{row['lane']} p{row['priority']}]")
    if view["blocked"]:
        print("\nblocked:")
        for row in view["blocked"]:
            print(f"  {row['id']} [{row['lane']}, {row['machine']}] {row['why']}")
    if view["recent"]:
        print("\nfinished in the last day:")
        for row in view["recent"]:
            res = ", ".join(f"{m}:{r}" for m, r in row["results"].items())
            print(f"  {row['finished']} {row['id']} {res} - {row['reason'][:90]}")


def cmd_status(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    cfg = q.config()
    if args.job:
        job = q.jobs().get(args.job)
        if not job:
            raise SystemExit(f"no job {args.job}")
        print(json.dumps(job, indent=2))
        for m, r in sorted(q.done().get(args.job, {}).items()):
            print(f"--- done on {m}")
            print(json.dumps(r, indent=2))
        return 0
    view = queue_view(q, cfg)
    if args.json:
        print(json.dumps(view, indent=2))
    else:
        print_view(view)
    return 0


# ---------------------------------------------------------------- coordinator commands

def edit_config(fn) -> dict:
    q = Queue(find_drive_root() / "render-queue")
    cfg = read_json(q.root / "config.json", None) or json.loads(json.dumps(DEFAULT_CONFIG))
    fn(cfg)
    write_json(q.root / "config.json", cfg)
    return cfg


def cmd_gate(args) -> int:
    machines = MACHINES if args.machine == "both" else (args.machine,)
    cfg = edit_config(lambda c: [c.setdefault("gates", {}).__setitem__(m, args.state) for m in machines])
    print("gates:", cfg["gates"])
    return 0


def cmd_hold(args) -> int:
    def f(c):
        held = [h for h in c.get("held", []) if h != args.id]
        if args.cmd == "hold":
            held.append(args.id)
        c["held"] = held
    print("held:", edit_config(f)["held"])
    return 0


def cmd_prioritize(args) -> int:
    cfg = edit_config(lambda c: c.setdefault("priority_overrides", {}).__setitem__(args.id, args.priority))
    print("priority overrides:", cfg["priority_overrides"])
    return 0


def cmd_prefer(args) -> int:
    def f(c):
        if args.machine == "off":
            c["prefer"] = None
        else:
            c["prefer"] = {"machine": args.machine, "wait_seconds": args.wait,
                           "lanes": args.lanes.split(",")}
    print("prefer:", edit_config(f)["prefer"])
    return 0


def cmd_cancel(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    if args.id not in q.jobs():
        raise SystemExit(f"no job {args.id}")
    p = q.d("cancel") / args.id
    if not p.exists():
        p.write_text(now_iso() + "\n", encoding="utf-8")
    print(f"cancel requested for {args.id}")
    return 0


# ---------------------------------------------------------------- process control

def proc_alive(pid) -> bool:
    if not pid:
        return False
    if IS_WINDOWS:
        import ctypes
        k32 = ctypes.windll.kernel32
        h = k32.OpenProcess(0x1000, False, int(pid))  # PROCESS_QUERY_LIMITED_INFORMATION
        if not h:
            return False
        code = ctypes.c_ulong()
        ok = k32.GetExitCodeProcess(h, ctypes.byref(code))
        k32.CloseHandle(h)
        return bool(ok) and code.value == 259  # STILL_ACTIVE
    try:
        os.kill(int(pid), 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    r = subprocess.run(["ps", "-o", "stat=", "-p", str(pid)], stdout=subprocess.PIPE, universal_newlines=True)
    return bool(r.stdout.strip()) and not r.stdout.strip().startswith("Z")


def process_table() -> list:
    """[(pid, ppid, stopped, command line)] for every process."""
    rows = []
    if IS_WINDOWS:
        r = run(["powershell", "-NoProfile", "-Command",
                 "Get-CimInstance Win32_Process | ForEach-Object { \"$($_.ProcessId)`t$($_.ParentProcessId)`t$($_.CommandLine)\" }"],
                check=False, timeout=60)
        for line in r.stdout.splitlines():
            parts = line.split("\t", 2)
            if len(parts) == 3 and parts[0].isdigit() and parts[1].isdigit():
                rows.append((int(parts[0]), int(parts[1]), False, parts[2]))
    else:
        r = subprocess.run(["ps", "-axo", "pid=,ppid=,stat=,command="], stdout=subprocess.PIPE,
                           universal_newlines=True)
        for line in r.stdout.splitlines():
            parts = line.split(None, 3)
            if len(parts) == 4:
                rows.append((int(parts[0]), int(parts[1]), parts[2].startswith("T"), parts[3]))
    return rows


def descendants(table: list, roots) -> set:
    kids: dict = {}
    for pid, ppid, _, _ in table:
        kids.setdefault(ppid, []).append(pid)
    out, stack = set(), [int(r) for r in roots if r]
    while stack:
        p = stack.pop()
        if p not in out:
            out.add(p)
            stack.extend(kids.get(p, []))
    return out


def kill_tree(pid) -> None:
    if not pid:
        return
    if IS_WINDOWS:
        run(["taskkill", "/T", "/F", "/PID", str(pid)], check=False)
        return
    for sig in (signal.SIGCONT, signal.SIGTERM):
        try:
            os.killpg(int(pid), sig)
        except (ProcessLookupError, PermissionError):
            pass
    for _ in range(20):
        if not proc_alive(pid):
            return
        time.sleep(0.5)
    try:
        os.killpg(int(pid), signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        pass


def signal_group(pgid, sig) -> None:
    try:
        os.killpg(int(pgid), sig)
    except (ProcessLookupError, PermissionError):
        pass


def python_gui() -> str:
    """The windowless interpreter on Windows, the ordinary one elsewhere."""
    exe = Path(sys.executable)
    if IS_WINDOWS and exe.name.lower() == "python.exe":
        w = exe.with_name("pythonw.exe")
        if w.exists():
            return str(w)
    return str(exe)


def python_console() -> str:
    exe = Path(sys.executable)
    if IS_WINDOWS and exe.name.lower() == "pythonw.exe":
        return str(exe.with_name("python.exe"))
    return str(exe)


def spawn_detached(argv, log_path: Path):
    """Start a process that outlives its parent: hidden on Windows and broken away
    from the Task Scheduler's job object; its own session (process group) on macOS."""
    out = open(log_path, "ab")
    if IS_WINDOWS:
        flags = 0x08000000 | 0x00000200  # CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP
        try:
            return subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                                    creationflags=flags | 0x01000000, close_fds=True)  # BREAKAWAY_FROM_JOB
        except OSError:
            return subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                                    creationflags=flags, close_fds=True)
    return subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                            start_new_session=True, close_fds=True)


# ---------------------------------------------------------------- wrapper

def cmd_wrap(args) -> int:
    """One job's process: run the command, record its pid and exit status locally."""
    allow_dataless_reads()
    run_dir = Path(args.run_dir)
    launch = read_json(run_dir / "launch.json")
    log = open(run_dir / "job.log", "ab")
    log.write(f"\n==== {now_iso()} launch {launch['argv']}\n".encode())
    log.flush()
    env = dict(os.environ)
    env.update(launch.get("env", {}))
    low = launch.get("low_priority", True)
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = 0x08000000 | (0x00004000 if low else 0)  # BELOW_NORMAL_PRIORITY_CLASS
    else:
        kw["start_new_session"] = True
        if low:
            kw["preexec_fn"] = lambda: (os.nice(10), allow_dataless_reads())
        else:
            kw["preexec_fn"] = allow_dataless_reads
    start = time.time()
    try:
        child = subprocess.Popen(launch["argv"], cwd=launch["cwd"], env=env, stdin=subprocess.DEVNULL,
                                 stdout=log, stderr=subprocess.STDOUT, **kw)
    except OSError as e:
        write_json(run_dir / "exit.json", {"exit_code": None, "error": str(e), "finished": now_iso()})
        return 1
    write_json(run_dir / "pid.json", {"wrapper": os.getpid(), "child": child.pid, "started": now_iso()})
    code = child.wait()
    write_json(run_dir / "exit.json", {"exit_code": code, "finished": now_iso(),
                                       "elapsed_s": round(time.time() - start, 1)})
    return 0


# ---------------------------------------------------------------- runner

class Runner:
    def __init__(self):
        self.machine = this_machine()
        self.local = local_root()
        self.settings = read_json(self.local / "machine.json", {}) or {}
        self.drive = Path(self.settings.get("drive") or find_drive_root())
        self.q = Queue(self.drive / "render-queue")
        self.state_path = self.local / "state.json"
        self.state = read_json(self.state_path, {}) or {}
        self.state.setdefault("active", {})  # job_id -> record
        self.cfg = self.q.config()
        self.awake = None
        self.reported_unreadable: set = set()
        self.note = None
        self.last_status = None
        self.last_status_time = 0.0
        self.last_sweep = 0.0
        self.copy_error_logged = set()

    # -- bookkeeping
    def save(self):
        write_json(self.state_path, self.state)

    def log(self, msg):
        self.q.log(self.machine, msg)

    def tags(self) -> list:
        return list(self.settings.get("tags", []))

    def placeholders(self, job_id: str, rec: dict) -> dict:
        p = {
            "drive": str(self.drive),
            "queue": str(self.q.root),
            "home": str(Path.home()),
            "repo": self.settings.get("repo", ""),
            "blender": self.settings.get("blender", "blender"),
            "python": self.settings.get("python", python_console()),
            "ffmpeg": self.settings.get("ffmpeg", "ffmpeg"),
            "machine": self.machine,
            "job": job_id,
            "work": str(self.local / "work" / job_id),
        }
        if rec.get("checkout"):
            p["checkout"] = rec["checkout"]
        return p

    @staticmethod
    def expand(value, ph: dict):
        if isinstance(value, str):
            for k, v in ph.items():
                value = value.replace("{" + k + "}", v)
            return value
        if isinstance(value, list):
            return [Runner.expand(v, ph) for v in value]
        if isinstance(value, dict):
            return {k: Runner.expand(v, ph) for k, v in value.items()}
        return value

    # -- the loop
    def loop(self):
        self.log(f"runner {VERSION} started pid {os.getpid()} python {sys.version.split()[0]} "
                 f"tags {','.join(self.tags())}")
        while True:
            self.cfg = self.q.config()
            try:
                self.tick()
            except Exception:
                self.log("tick error: " + traceback.format_exc().replace("\n", " | "))
            swapping = any(r.get("swap") for r in self.state["active"].values())
            time.sleep(3 if swapping else max(5, int(self.cfg.get("poll_seconds", 20))))

    def tick(self):
        cfg = self.cfg
        jobs = self.q.jobs()
        for name in sorted(self.q.unreadable - self.reported_unreadable):
            self.log(f"cannot read jobs/{name}; it is skipped until it reads")
        self.reported_unreadable |= self.q.unreadable
        self.q.unreadable = set()
        self.reap(jobs)
        self.handle_cancels(jobs)
        done = self.q.done()
        self.copy_incremental(jobs)
        self.note = None
        running = self.running_id()
        if running:
            rec = self.state["active"][running]
            job = jobs[running]
            want = self.swap_reason(running, job, rec, jobs, done)
            if rec.get("swap") and not want:
                self.log(f"swap-out of {running} called off ({rec['swap']['why']} no longer applies)")
                rec.pop("swap")
                self.save()
            elif want and not rec.get("swap"):
                self.request_swap(running, job, rec, want)
            if rec.get("swap"):
                if self.swap_ready(running, job, rec):
                    self.suspend(running, rec["swap"]["why"])
                    running = None
                else:
                    self.note = f"waiting for {running}'s frame in progress before swapping it out"
        if not running:
            best = self.pick(jobs, done)
            if best:
                self.start_or_resume(best, jobs[best])
        if not self.running_id() and time.time() - self.last_sweep > 600:
            self.last_sweep = time.time()
            try:
                self.sweep_orphans(jobs, done)
            except Exception:
                self.log("sweep error: " + traceback.format_exc().replace("\n", " | "))
        self.set_awake(self.running_id() is not None)
        self.publish(jobs)

    def running_id(self):
        for jid, rec in self.state["active"].items():
            if rec.get("state") in ("running", "preparing"):
                return jid
        return None

    def swap_reason(self, jid, job, rec, jobs, done):
        """Why the running job should be swapped out now, or None."""
        cfg = self.cfg
        if jid in cfg.get("held", []):
            return "held"
        if not gate_allows(job, cfg, self.machine):
            return "paused"
        if job["lane"] == "light" and time.time() - rec.get("started_epoch", time.time()) > \
                int(cfg.get("light_max_seconds", 1800)):
            return None  # handled by reap as a failure
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec))
        ran = time.time() - rec.get("started_epoch", time.time())
        if cfg.get("preempt", True) and fr and self.count_frames(fr) < int(fr["count"]) - 1                 and ran >= int(cfg.get("preempt_min_run_seconds", 1200)):
            # Only a job with frames still to render after the one in progress can
            # yield at a frame boundary; anything else finishes first.
            # Not dry: an `any` job is claimed (and its claim wait sat out) while this
            # job keeps rendering, so the swap itself costs no idle time.
            if self.pick(jobs, done, swap_in_for=job):
                return "preempted"
        return None

    def request_swap(self, jid, job, rec, why):
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec))
        rec["swap"] = {"why": why, "since": time.time(),
                       "frames_at": self.count_frames(fr) if fr else None}
        self.save()
        if fr and self.cfg.get("swap_at_frame_boundary", True):
            self.log(f"{why}: swapping {jid} out after the frame in progress lands "
                     f"({rec['swap']['frames_at']}/{fr['count']} done)")

    def swap_ready(self, jid, job, rec) -> bool:
        sw = rec["swap"]
        if sw.get("frames_at") is None:
            return True
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec))
        have = self.count_frames(fr)
        if have >= int(fr["count"]):
            return False  # its last frame landed: let it exit and complete normally
        if have > sw["frames_at"]:
            return True
        # Preemption never stops a frame mid-render. Pause and hold give up after
        # swap_wait_max_seconds, or at once with swap_at_frame_boundary off.
        if sw["why"] == "preempted":
            return False
        if not self.cfg.get("swap_at_frame_boundary", True):
            return True
        if time.time() - sw["since"] > int(self.cfg.get("swap_wait_max_seconds", 420)):
            self.log(f"{jid}: no frame landed within swap_wait_max_seconds; stopping mid-frame")
            return True
        return False

    def pick(self, jobs, done, swap_in_for=None, dry=False):
        """The next job to run here. With swap_in_for, only jobs allowed to swap that
        final out (an earlier lane). dry: report a candidate without claiming it."""
        cfg = self.cfg
        lanes = lane_order(cfg)
        tags = set(self.tags())
        cands = []
        for jid, job in jobs.items():
            if job.get("machine") not in (self.machine, "any"):
                continue
            rec = self.state["active"].get(jid)
            if rec and rec.get("state") in ("running", "preparing"):
                continue
            if swap_in_for is not None:
                if lanes.index(job["lane"]) >= lanes.index(swap_in_for["lane"]):
                    continue
                if job["machine"] == "any" and rec is None and \
                        time.time() - float(job.get("submitted_epoch", 0)) < int(cfg.get("any_grace_seconds", 120)):
                    continue  # leave it a moment for an idle machine
            if jid in done and (self.machine in done[jid] or job_succeeded(done[jid]) or
                                any(r.get("result") == "unreadable" for r in done[jid].values())):
                continue
            if self.q.cancelled(jid) or jid in cfg.get("held", []):
                continue
            if not gate_allows(job, cfg, self.machine):
                continue
            if not set(job.get("requires", [])) <= tags:
                continue
            if any(not job_succeeded(done.get(a, {})) for a in job.get("after", [])):
                continue
            if job["machine"] == "any" and rec is None:
                w = claim_winner(self.q.claims(jid))
                if w and w != self.machine:
                    continue
                if not w and self.leave_to_preferred(job):
                    continue
            # A job preempted before it had run preempt_min_run_seconds is owed the
            # rest of that run: it goes back in ahead of any queued earlier-lane job,
            # so back-to-back light jobs cannot keep a final swapped out.
            owed = swap_in_for is None and rec is not None and rec.get("why") == "preempted" and                 float(rec.get("last_ran", 0)) < int(cfg.get("preempt_min_run_seconds", 1200))
            cands.append((0 if owed else 1,) + order_key(job, cfg, resumed=rec is not None))
        for c in sorted(cands):
            jid = c[-1]
            if dry:
                return jid
            if jobs[jid]["machine"] == "any" and jid not in self.state["active"]:
                if not self.claim(jid):
                    continue
            if jid not in self.state["active"] and self.foreign_blender():
                return None
            return jid
        return None

    def leave_to_preferred(self, job) -> bool:
        """True while config `prefer` names another machine for this job's lane, that
        machine's runner is alive with its gate open, and the job has waited less
        than wait_seconds."""
        pref = self.cfg.get("prefer")
        if not isinstance(pref, dict) or pref.get("machine") in (None, self.machine):
            return False
        if job["lane"] not in pref.get("lanes", ["review", "final"]):
            return False
        if time.time() - float(job.get("submitted_epoch", 0)) >= int(pref.get("wait_seconds", 1800)):
            return False
        other = pref["machine"]
        if self.cfg.get("gates", {}).get(other) != "run" and job["lane"] not in self.cfg.get("ungated_lanes", ["light"]):
            return False
        st = self.q.statuses().get(other, {})
        if not st.get("updated_epoch") or time.time() - st["updated_epoch"] > 900:
            return False  # preferred runner down: don't leave work waiting on it
        return True

    def foreign_blender(self) -> bool:
        """A background Blender this runner did not start (a hand-started render)."""
        if not self.cfg.get("block_on_foreign_blender", True):
            return False
        table = process_table()
        ours = descendants(table, [r.get("wrapper") for r in self.state["active"].values()])
        foreign = [pid for pid, _, stopped, cmd in table
                   if pid not in ours and not stopped and re.search(r"blender(\.exe)?\b", cmd, re.I)
                   and re.search(r"\s(-b|--background)\b", cmd)]
        if foreign:
            self.note = f"not starting: a Blender this runner did not start is rendering (pid {foreign[0]})"
            return True
        return False

    def claim(self, jid) -> bool:
        path = self.q.d("claims") / f"{jid}.{self.machine}"
        if not path.exists():
            write_json(path, {"machine": self.machine, "time": time.time(), "at": now_iso()})
            wait = int(self.cfg.get("claim_wait_seconds", 90))
            self.log(f"claimed {jid}; waiting {wait}s for the other machine's claim to sync")
            self.note = f"claiming {jid}"
            self.publish(None)
            time.sleep(wait)
        w = claim_winner(self.q.claims(jid))
        if w != self.machine:
            self.log(f"lost claim on {jid} to {w}")
            return False
        self.log(f"won claim on {jid}")
        return True

    # -- job lifecycle
    def start_or_resume(self, jid, job):
        rec = self.state["active"].get(jid)
        if rec and rec.get("state") == "frozen" and proc_alive(rec.get("pgid")):
            signal_group(rec["pgid"], signal.SIGCONT)
            rec["state"] = "running"
            rec.pop("why", None)
            self.save()
            self.log(f"swapped {jid} back in (SIGCONT)")
            return
        if rec is None or rec.get("state") == "preparing":
            if rec is None:
                rec = {"id": jid, "lane": job["lane"], "attempts": 0,
                       "first_started": now_iso(), "first_started_epoch": time.time()}
                self.state["active"][jid] = rec
            rec["state"] = "preparing"
            self.save()
            self.log(f"preparing {jid}")
            try:
                self.prepare(jid, job, rec)
            except Exception as e:
                self.finish(jid, job, rec, "failed", reason=f"input preparation failed: {e}")
                return
        elif rec.get("why"):
            self.log(f"swapping {jid} back in (was {rec['why']})")
        self.launch(jid, job, rec)

    def prepare(self, jid, job, rec):
        (self.local / "work" / jid).mkdir(parents=True, exist_ok=True)
        for inp in job.get("inputs", []):
            if inp["kind"] == "git":
                rec["checkout"] = str(self.checkout(jid, inp))
                self.save()
        ph = self.placeholders(jid, rec)
        for inp in job.get("inputs", []):
            if inp["kind"] == "drive":
                self.copy_drive_input(self.expand(inp, ph))

    def checkout(self, jid, inp) -> Path:
        repo = Path(self.settings["repo"])
        commit = inp["commit"]

        def have():
            return run(["git", "-C", repo, "cat-file", "-e", commit + "^{commit}"], check=False).returncode == 0

        if not have():
            run(["git", "-C", repo, "fetch", "--quiet", "origin"], check=False, timeout=600)
        if not have() and inp.get("hub_ref"):
            run(["git", "-C", repo, "fetch", "--quiet", "hub", f"+{inp['hub_ref']}:{inp['hub_ref']}"],
                timeout=600, env=git_env())
        if not have() and inp.get("bundle"):
            bundle = self.q.root / inp["bundle"]
            v = run(["git", "-C", repo, "bundle", "verify", bundle], check=False)
            if v.returncode != 0:
                raise RuntimeError("bundle does not verify against this clone: " + v.stdout[-400:])
            run(["git", "-C", repo, "fetch", "--quiet", bundle,
                 f"{inp.get('bundle_ref')}:refs/render-queue/{jid}"], timeout=600)
        if not have():
            raise RuntimeError(f"commit {commit} is not available here (not on origin/main, "
                               f"no hub ref or bundle reached this clone)")
        dest = repo / ".claude" / "worktrees" / f"rq-{jid}"
        if dest.exists():
            head = run(["git", "-C", dest, "rev-parse", "HEAD"], check=False).stdout.strip()
            if head == commit:
                return dest
            raise RuntimeError(f"{dest} exists at another commit")
        run(["git", "-C", repo, "worktree", "add", "--detach", dest, commit], timeout=600)
        return dest

    def copy_drive_input(self, inp):
        src, dst = Path(inp["from"]), Path(inp["to"])
        if src.is_dir():
            dst.mkdir(parents=True, exist_ok=True)
            for f in src.rglob("*"):
                if f.is_file():
                    t = dst / f.relative_to(src)
                    if not (t.exists() and t.stat().st_size == f.stat().st_size):
                        t.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(f, t)
        else:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
        checks = dict(inp.get("sha256", {}))
        if inp.get("manifest"):
            man = read_json(Path(inp["manifest"]))
            if not man or not man.get("asset") or not man.get("asset_sha256"):
                raise RuntimeError(f"manifest {inp['manifest']} has no asset/asset_sha256")
            checks[man["asset"]] = man["asset_sha256"]
        base = dst if dst.is_dir() else dst.parent
        for rel, want in checks.items():
            got = sha256_file(base / rel)
            if got != want:
                raise RuntimeError(f"{base / rel}: sha256 {got[:12]} != manifest {want[:12]}")

    def frame_files(self, fr: dict):
        d = Path(fr["dir"])
        if not d.is_dir():
            return []
        pat = fr.get("glob", "*.png")
        return [p for p in d.iterdir() if p.is_file() and fnmatch.fnmatch(p.name, pat)]

    def count_frames(self, fr: dict) -> int:
        return len([p for p in self.frame_files(fr) if p.stat().st_size > 0])

    def launch(self, jid, job, rec):
        ph = self.placeholders(jid, rec)
        fr = self.expand(job.get("frames"), ph)
        if fr:
            for p in self.frame_files(fr):
                if p.stat().st_size == 0:
                    p.unlink()  # an interrupted frame's placeholder would be skipped
        cmd = job["command"]
        if isinstance(cmd, dict):
            cmd = cmd.get(self.machine)
            if not cmd:
                self.finish(jid, job, rec, "failed", reason=f"no command for {self.machine}")
                return
        argv = self.expand(cmd, ph)
        cwd = self.expand(job.get("cwd") or "{work}", ph)
        env = self.expand(job.get("env", {}), ph)
        extra_path = self.settings.get("path_prepend", [])
        if extra_path:
            env["PATH"] = os.pathsep.join(extra_path + [env.get("PATH", os.environ.get("PATH", ""))])
        run_dir = self.local / "run" / jid
        run_dir.mkdir(parents=True, exist_ok=True)
        for stale in ("exit.json", "pid.json"):
            (run_dir / stale).unlink(missing_ok=True)
        low = bool(job.get("low_priority", self.cfg.get("low_priority", True)))
        write_json(run_dir / "launch.json", {"argv": argv, "cwd": cwd, "env": env, "low_priority": low})
        installed = self.local / "render_queue.py"
        script = installed if installed.exists() else Path(__file__).resolve()
        p = spawn_detached([python_gui(), str(script), "wrap", str(run_dir)], run_dir / "wrapper.log")
        rec.update(state="running", wrapper=p.pid, attempts=rec.get("attempts", 0) + 1,
                   started=now_iso(), started_epoch=time.time(), run_dir=str(run_dir))
        for k in ("pgid", "why", "swap"):
            rec.pop(k, None)
        self.save()
        shown = " ".join(argv).replace("\n", "\\n")
        self.log(f"launched {jid} attempt {rec['attempts']}: {shown[:300]}")

    def child_pid(self, rec):
        return (read_json(Path(rec["run_dir"]) / "pid.json", {}) or {}).get("child") if rec.get("run_dir") else None

    def stop_processes(self, rec):
        """Stop the job's whole process tree and wait until it is gone."""
        if IS_WINDOWS:
            kill_tree(rec.get("wrapper"))
        else:
            child = self.child_pid(rec)
            if child:
                kill_tree(child)
        for _ in range(60):
            if not proc_alive(rec.get("wrapper")):
                return
            time.sleep(0.5)
        self.log(f"warning: wrapper {rec.get('wrapper')} still alive after stop")

    def suspend(self, jid, why):
        rec = self.state["active"][jid]
        if not IS_WINDOWS and self.cfg.get("mac_swap", "stop") == "freeze" and self.child_pid(rec):
            rec["pgid"] = self.child_pid(rec)
            signal_group(rec["pgid"], signal.SIGSTOP)
            rec["state"] = "frozen"
            self.log(f"{why}: froze {jid} (SIGSTOP)")
        else:
            self.stop_processes(rec)
            rec["state"] = "stopped"
            self.log(f"{why}: stopped {jid}; it relaunches later and skips finished frames")
        rec["why"] = why
        rec["last_ran"] = round(time.time() - rec.get("started_epoch", time.time()), 1)
        rec.pop("swap", None)
        self.save()

    def reap(self, jobs):
        for jid, rec in list(self.state["active"].items()):
            job = jobs.get(jid)
            if job is None:
                self.log(f"job file for {jid} vanished; dropping local record")
                del self.state["active"][jid]
                self.save()
                continue
            if rec.get("state") == "preparing":
                # The runner stopped while preparing; prepare again and launch.
                self.start_or_resume(jid, job)
                continue
            if rec.get("state") != "running":
                continue
            ex = read_json(Path(rec["run_dir"]) / "exit.json")
            if ex is not None:
                self.complete(jid, job, rec, ex)
            elif job["lane"] == "light" and time.time() - rec.get("started_epoch", time.time()) > \
                    int(self.cfg.get("light_max_seconds", 1800)):
                self.stop_processes(rec)
                self.finish(jid, job, rec, "failed",
                            reason=f"light job ran past light_max_seconds ({self.cfg.get('light_max_seconds')}s)")
            elif not proc_alive(rec.get("wrapper")) and time.time() - rec.get("started_epoch", 0) > 15:
                # Wrapper gone without recording an exit: reboot, kill from outside.
                if rec.get("attempts", 0) >= 3:
                    self.finish(jid, job, rec, "failed", reason="process vanished three times")
                else:
                    self.log(f"{jid}: wrapper vanished without an exit record; relaunching")
                    self.launch(jid, job, rec)

    def complete(self, jid, job, rec, ex):
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec))
        code = ex.get("exit_code")
        if fr:
            have = self.count_frames(fr)
            ok = have >= int(fr["count"])
            reason = f"{have}/{fr['count']} frames, exit {code}"
        else:
            ok = code == 0
            reason = f"exit {code}" + (f" ({ex.get('error')})" if ex.get("error") else "")
        self.finish(jid, job, rec, "ok" if ok else "failed", reason=reason, exit_code=code)

    def output_pairs(self, jid, job, rec):
        ph = self.placeholders(jid, rec)
        for o in self.expand(job.get("outputs", []), ph):
            src = Path(o["from"])
            dst = Path(o.get("to") or str(self.q.root / "out" / jid / self.machine))
            files = [src] if src.is_file() else sorted(
                p for p in src.glob(o.get("glob", "*")) if p.is_file()) if src.is_dir() else []
            for f in files:
                yield f, dst / f.name

    def copy_outputs(self, jid, job, rec, settle_seconds=0.0) -> list:
        """Copy, never move. With settle_seconds, only files untouched that long
        (a frame Blender has finished writing)."""
        copied = []
        for f, t in self.output_pairs(jid, job, rec):
            st = f.stat()
            if st.st_size == 0 or time.time() - st.st_mtime < settle_seconds:
                continue
            if not (t.exists() and t.stat().st_size == st.st_size):
                t.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, t)
            copied.append(str(t))
        return copied

    def copy_incremental(self, jobs):
        for jid, rec in self.state["active"].items():
            job = jobs.get(jid)
            if job and rec.get("state") == "running" and job.get("frames") and job.get("copy_incrementally", True):
                try:
                    self.copy_outputs(jid, job, rec, settle_seconds=20)
                except OSError as e:
                    self.log(f"{jid}: incremental copy failed: {e}")

    def finish(self, jid, job, rec, result, reason="", exit_code=None):
        copied, copy_err = [], None
        if result == "ok":
            try:
                copied = self.copy_outputs(jid, job, rec)
            except Exception as e:
                copy_err = str(e)
                result = "failed"
                reason += f"; output copy failed: {e}"
        tail = []
        if rec.get("run_dir"):
            try:
                with open(Path(rec["run_dir"]) / "job.log", "rb") as f:
                    f.seek(0, 2)
                    f.seek(max(0, f.tell() - 6000))
                    tail = f.read().decode("utf-8", "replace").splitlines()[-40:]
            except OSError:
                pass
        out = {
            "id": jid, "machine": self.machine, "result": result, "reason": reason,
            "exit_code": exit_code, "attempts": rec.get("attempts", 0),
            "started": rec.get("first_started"), "finished": now_iso(), "finished_epoch": time.time(),
            "elapsed_s": round(time.time() - rec.get("first_started_epoch", time.time()), 1),
            "outputs": copied, "checkout": rec.get("checkout"), "work": str(self.local / "work" / jid),
            "log_tail": tail, "runner": VERSION,
        }
        if copy_err:
            out["copy_error"] = copy_err
        if rec.get("run_dir") and (Path(rec["run_dir"]) / "job.log").exists():
            try:
                shutil.copy2(Path(rec["run_dir"]) / "job.log", self.q.d("log/jobs") / f"{jid}.{self.machine}.log")
                out["log"] = f"log/jobs/{jid}.{self.machine}.log"
            except OSError:
                pass
        write_json(self.q.d("done") / f"{jid}.{self.machine}", out)
        self.log(f"{result} {jid}: {reason}; {len(copied)} file(s) copied")
        del self.state["active"][jid]
        self.save()
        # Nothing is removed here: an output may live in the checkout or the scratch,
        # and the Drive copy may not have uploaded yet. The idle sweep removes both
        # once the done record is old enough (retain_ok_hours / retain_failed_hours).

    def remove_work(self, jid):
        work = self.local / "work" / jid
        if work.exists():
            shutil.rmtree(work, ignore_errors=True)
            if work.exists():
                self.log(f"could not remove scratch {work}")

    def sweepable(self, jid, jobs, done, path) -> bool:
        """True when a finished job's local checkout or scratch may go: the job is
        not active or queued, not keep_checkout, its own done record here shows no
        failed output copy, and that record is older than the retention for its
        result. A folder with no job file and no record of this machine is swept
        only once it is older than the failed-job retention."""
        if jid in self.state["active"] or jobs.get(jid, {}).get("keep_checkout"):
            return False
        if jid in jobs and jid not in done:
            return False
        rec = done.get(jid, {}).get(self.machine)
        now = time.time()
        hours = float(self.cfg.get("retain_failed_hours", 48))
        if rec is None:
            try:
                return now - path.stat().st_mtime > hours * 3600
            except OSError:
                return False
        if rec.get("copy_error") or rec.get("result") == "unreadable":
            if jid not in self.copy_error_logged:
                self.copy_error_logged.add(jid)
                self.log(f"keeping {path}: {jid} has no clean output copy; copy it by hand, then delete")
            return False
        if rec.get("result") == "ok":
            hours = float(self.cfg.get("retain_ok_hours", 12))
        finished = rec.get("finished_epoch")
        return isinstance(finished, (int, float)) and now - finished > hours * 3600

    def sweep_orphans(self, jobs, done):
        """Remove rq-* checkouts and work/ folders of finished jobs once sweepable()
        allows it, then prune dead worktree entries."""
        repo = self.settings.get("repo")
        swept = 0
        wt = Path(repo) / ".claude" / "worktrees" if repo else None
        if wt and wt.is_dir():
            for p in sorted(wt.glob("rq-*")):
                jid = p.name[3:]
                if not self.sweepable(jid, jobs, done, p):
                    continue
                self.cleanup_inputs(jid, jobs.get(jid, {}), {"checkout": str(p)})
                if p.exists():
                    shutil.rmtree(p, ignore_errors=True)
                if not p.exists():
                    swept += 1
        wk = self.local / "work"
        if wk.is_dir():
            for p in sorted(wk.iterdir()):
                if p.is_dir() and self.sweepable(p.name, jobs, done, p):
                    self.remove_work(p.name)
                    swept += 1
        if repo:
            run(["git", "-C", repo, "worktree", "prune"], check=False)
        if swept:
            self.log(f"swept {swept} orphaned checkout/scratch folder(s)")

    def cleanup_inputs(self, jid, job, rec):
        repo = self.settings.get("repo")
        if rec.get("checkout"):
            r = run(["git", "-C", repo, "worktree", "remove", "--force", rec["checkout"]], check=False)
            if r.returncode != 0:
                self.log(f"could not remove checkout {rec['checkout']}: {r.stdout.strip()[:200]}")
        run(["git", "-C", repo, "update-ref", "-d", f"refs/render-queue/{jid}"], check=False)
        for inp in job.get("inputs", []):
            ref = inp.get("hub_ref")
            if ref:
                run(["git", "-C", repo, "update-ref", "-d", ref], check=False)
                r = run(["git", "-C", repo, "push", "--quiet", "hub", f":{ref}"], check=False, timeout=120,
                        env=git_env())
                if r.returncode != 0:
                    self.log(f"could not delete {ref} on hub: {r.stdout.strip()[:200]}")

    def handle_cancels(self, jobs):
        for jid, rec in list(self.state["active"].items()):
            if self.q.cancelled(jid):
                if rec.get("state") in ("running", "frozen"):
                    self.stop_processes(rec)
                self.finish(jid, jobs.get(jid, {}), rec, "cancelled", reason="cancel requested")

    # -- keep awake while working
    def set_awake(self, on: bool):
        if IS_WINDOWS:
            import ctypes
            flags = 0x80000000 | (0x00000001 if on else 0)  # ES_CONTINUOUS | ES_SYSTEM_REQUIRED
            ctypes.windll.kernel32.SetThreadExecutionState(flags)
            return
        if on and (self.awake is None or self.awake.poll() is not None):
            self.awake = subprocess.Popen(["caffeinate", "-i", "-w", str(os.getpid())])
        elif not on and self.awake is not None:
            self.awake.terminate()
            self.awake = None

    # -- status
    def progress(self, jid, job, rec) -> dict:
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec)) if job else None
        if not fr:
            return {}
        frames = sorted(p.stat().st_mtime for p in self.frame_files(fr) if p.stat().st_size > 0)
        have, count = len(frames), int(fr["count"])
        out = {"progress": f"{have}/{count} frames", "frames_done": have, "frames_total": count}
        if rec.get("state") == "running":
            recent = [t for t in frames if t >= rec.get("started_epoch", 0)][-11:]
            gaps = [b - a for a, b in zip(recent, recent[1:]) if b > a]
            if gaps:
                spf = statistics.median(gaps)
                out["sec_per_frame"] = round(spf, 1)
                out["eta_s"] = int(max(0, count - have) * spf)
        return out

    def publish(self, jobs):
        jobs = jobs if jobs is not None else self.q.jobs()
        cur, suspended = None, []
        for jid, rec in self.state["active"].items():
            slot = {"id": jid, "state": rec.get("state"), "lane": rec.get("lane"),
                    "attempts": rec.get("attempts"), "started": rec.get("started")}
            slot.update(self.progress(jid, jobs.get(jid), rec))
            if rec.get("state") in ("running", "preparing"):
                if rec.get("swap"):
                    slot["swap"] = rec["swap"]["why"]
                cur = slot
            else:
                why = rec.get("why")
                if why == "held" and jid not in self.cfg.get("held", []):
                    why = "released; resumes in its turn"
                elif why == "paused" and gate_allows(jobs.get(jid, {"lane": rec.get("lane")}), self.cfg, self.machine):
                    why = "gate open; resumes in its turn"
                slot["why"] = why
                suspended.append(slot)
        body = {"machine": self.machine, "gate": self.cfg.get("gates", {}).get(self.machine),
                "current": cur, "suspended": suspended, "note": self.note, "tags": self.tags(),
                "runner": f"{VERSION} pid {os.getpid()}", "host": socket.gethostname()}
        # Write on change, and every five minutes as a heartbeat.
        if body != self.last_status or time.time() - self.last_status_time > 300:
            self.last_status = json.loads(json.dumps(body))
            self.last_status_time = time.time()
            body.update(updated=now_iso(), updated_epoch=time.time())
            write_json(self.q.d("status") / f"{self.machine}.json", body)


def single_instance(local: Path):
    local.mkdir(parents=True, exist_ok=True)
    f = open(local / "runner.lock", "a+")
    try:
        if IS_WINDOWS:
            import msvcrt
            f.seek(0)
            msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(f.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        return None
    return f


def in_job_object() -> bool:
    import ctypes
    k32 = ctypes.windll.kernel32
    k32.GetCurrentProcess.restype = ctypes.c_void_p
    result = ctypes.c_int(0)
    k32.IsProcessInJob(ctypes.c_void_p(k32.GetCurrentProcess()), None, ctypes.byref(result))
    return bool(result.value)


def spawn_outside_job(argv, cwd) -> None:
    """Start a process through WMI, which creates it outside the caller's job
    object. Task Scheduler runs its task in a job object and ends that object
    when the task is stopped, taking every process in it along."""
    cmdline = " ".join(f'"{a}"' if " " in str(a) or "\\" in str(a) else str(a) for a in argv)
    ps = ("$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments "
          f"@{{CommandLine='{cmdline}'; CurrentDirectory='{cwd}'}}; "
          "if ($r.ReturnValue -ne 0) { exit $r.ReturnValue }; $r.ProcessId")
    r = run(["powershell", "-NoProfile", "-Command", ps], check=False, timeout=60)
    if r.returncode != 0:
        raise RuntimeError(f"Win32_Process.Create failed: {r.stdout.strip()[:300]}")


def cmd_runner(args) -> int:
    allow_dataless_reads()
    if IS_WINDOWS and not args.detached and in_job_object():
        # Started by Task Scheduler: move out of its job object first, so that
        # neither ending the task nor restarting the runner can reach a job.
        script = local_root() / "render_queue.py"
        spawn_outside_job([python_gui(), script if script.exists() else Path(__file__).resolve(),
                           "runner", "--detached"], local_root())
        return 0
    lock = single_instance(local_root())
    if lock is None:
        print("a runner is already running on this machine")
        return 0
    Runner().loop()
    return 0


# ---------------------------------------------------------------- install

TASK_NAME = "Starsector render queue"
PLIST_LABEL = "com.starsectormarines.renderqueue"


def detect_tags(settings: dict) -> list:
    tags = {"blender"} if Path(settings.get("blender", "")).exists() else set()
    if IS_WINDOWS:
        r = run(["nvidia-smi", "--query-gpu=name", "--format=csv,noheader"], check=False) \
            if shutil.which("nvidia-smi") else None
        if r and r.returncode == 0 and r.stdout.strip():
            tags |= {"gpu", "cuda", "optix", "gpu-" + re.sub(r"[^a-z0-9]+", "-", r.stdout.strip().lower()
                                                            .replace("nvidia geforce ", "")).strip("-")}
        if Path("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core").is_dir():
            tags.add("starsector")
        if Path("C:/Windows/Fonts/bahnschrift.ttf").exists():
            tags.add("font-bahnschrift")
    else:
        r = run(["sysctl", "-n", "hw.ncpu"], check=False)
        if r.returncode == 0:
            tags.add(f"cpu{r.stdout.strip()}")
        r = run(["sysctl", "-n", "machdep.cpu.brand_string"], check=False)
        if "Apple" in r.stdout:
            tags |= {"gpu", "metal", "apple-silicon"}
    if settings.get("ffmpeg") and Path(settings["ffmpeg"]).exists():
        tags.add("ffmpeg")
    return sorted(tags | set(settings.get("extra_tags", [])))


def cmd_install(args) -> int:
    local = local_root()
    local.mkdir(parents=True, exist_ok=True)
    drive = find_drive_root()
    q = Queue(drive / "render-queue")
    for sub in ("jobs", "inputs", "cancel", "claims", "done", "status", "log", "out"):
        q.d(sub)
    if not (q.root / "config.json").exists():
        write_json(q.root / "config.json", DEFAULT_CONFIG)
        print("wrote", q.root / "config.json", "(both gates pause)")
    src = Path(__file__).resolve()
    if src != (local / "render_queue.py").resolve():
        shutil.copy2(src, local / "render_queue.py")
    settings = read_json(local / "machine.json", {}) or {}
    ffmpeg = shutil.which("ffmpeg") or ("/opt/homebrew/bin/ffmpeg" if Path("/opt/homebrew/bin/ffmpeg").exists() else "")
    settings.update({k: v for k, v in {
        "machine": this_machine(), "drive": str(drive), "python": python_console(),
        "repo": args.repo or settings.get("repo") or str(repo_root()),
        "blender": args.blender or settings.get("blender") or default_blender(),
        "ffmpeg": settings.get("ffmpeg") or ffmpeg,
    }.items() if v})
    if not IS_WINDOWS:
        settings.setdefault("path_prepend", ["/opt/homebrew/bin", "/usr/local/bin"])
    if args.tag:
        settings["extra_tags"] = sorted(set(settings.get("extra_tags", [])) | set(args.tag))
    settings["tags"] = detect_tags(settings)
    write_json(local / "machine.json", settings)
    print("installed", local / "render_queue.py", "settings", json.dumps(settings))
    if args.no_service:
        return 0
    if IS_WINDOWS:
        task = run(["schtasks", "/Query", "/TN", TASK_NAME], check=False)
        if task.returncode == 0:
            # Restart only the runner process, outside any job object; never end
            # the task, which would end its job object and any job inside it.
            stop_runner_processes()
            spawn_outside_job([python_gui(), local / "render_queue.py", "runner", "--detached"], local)
            print("runner restarted outside the scheduled task's job object")
            return 0
        pyw = python_gui()
        ps = (
            f"$a = New-ScheduledTaskAction -Execute '{pyw}' -Argument '\"{local / 'render_queue.py'}\" runner' "
            f"-WorkingDirectory '{local}';"
            "$t = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME;"
            "$s = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries "
            "-DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -RestartCount 3 "
            "-RestartInterval (New-TimeSpan -Minutes 5);"
            f"Register-ScheduledTask -TaskName '{TASK_NAME}' -Action $a -Trigger $t -Settings $s "
            "-Description 'Starsector Marines render queue runner (tools/render_queue.py)' -Force | Out-Null;"
            f"Start-ScheduledTask -TaskName '{TASK_NAME}'"
        )
        r = run(["powershell", "-NoProfile", "-Command", ps], check=False)
        print(r.stdout.strip() or f"scheduled task '{TASK_NAME}' registered and started")
        return r.returncode
    if args.mode == "nohup":
        # Fallback for a Mac where the interpreter lacks Full Disk Access: a
        # LaunchAgent then cannot list ~/Library/CloudStorage ("Operation not
        # permitted"), while a process started from ssh or Terminal can. It does
        # not survive a reboot. Running jobs survive the runner's restart.
        uid = os.getuid()
        run(["launchctl", "bootout", f"gui/{uid}/{PLIST_LABEL}"], check=False)
        stop_runner_processes()
        run(["/bin/zsh", "-c", f"nohup /usr/bin/python3 '{local / 'render_queue.py'}' runner "
                               f">> '{local / 'runner.out'}' 2>&1 &!"])
        print("runner started under nohup; after a reboot run install again from ssh or Terminal")
        return 0
    # launchd charges file access to the program the plist names, so it must be
    # the interpreter that holds Full Disk Access (Python.app inside the Command
    # Line Tools framework), not the /usr/bin/python3 shim that execs it.
    interp = Path(sys.prefix) / "Resources/Python.app/Contents/MacOS/Python"
    if not interp.exists():
        interp = Path(os.path.realpath(sys.executable))
    print(f"LaunchAgent runs {interp}; it needs Full Disk Access "
          "(System Settings > Privacy & Security > Full Disk Access)")
    plist = Path.home() / "Library/LaunchAgents" / f"{PLIST_LABEL}.plist"
    text = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>{PLIST_LABEL}</string>
  <key>ProgramArguments</key><array>
    <string>{interp}</string><string>{local / 'render_queue.py'}</string><string>runner</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Interactive</string>
  <key>StandardOutPath</key><string>{local / 'runner.out'}</string>
  <key>StandardErrorPath</key><string>{local / 'runner.out'}</string>
</dict></plist>
"""
    loaded = run(["launchctl", "print", f"gui/{os.getuid()}/{PLIST_LABEL}"], check=False).returncode == 0
    if loaded and plist.exists() and plist.read_text(encoding="utf-8") == text:
        # Same agent: stop only the runner and let KeepAlive start the new script.
        # Jobs run in their own sessions and are adopted by the new runner.
        stop_runner_processes()
        print(f"runner restarted by launch agent {PLIST_LABEL}")
        return 0
    stop_runner_processes()
    plist.write_text(text, encoding="utf-8")
    uid = os.getuid()
    run(["launchctl", "bootout", f"gui/{uid}/{PLIST_LABEL}"], check=False)
    r = run(["launchctl", "bootstrap", f"gui/{uid}", str(plist)], check=False)
    print(r.stdout.strip() or f"launch agent {PLIST_LABEL} loaded")
    return r.returncode


def stop_runner_processes() -> None:
    """Stop the runner loop on this machine, and nothing else: never a job's
    wrapper or its process tree."""
    me = os.getpid()
    for pid, _, _, cmd in process_table():
        if pid != me and "render_queue.py" in cmd and re.search(r"render_queue\.py\"?\s+runner(\s|$)", cmd):
            if IS_WINDOWS:
                run(["taskkill", "/F", "/PID", str(pid)], check=False)
            else:
                try:
                    os.kill(pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
    time.sleep(2)


def default_blender() -> str:
    if IS_WINDOWS:
        hits = sorted(glob.glob("C:/Program Files/Blender Foundation/Blender */blender.exe"))
        return hits[-1].replace("\\", "/") if hits else "blender"
    return "/Applications/Blender.app/Contents/MacOS/Blender"


# ---------------------------------------------------------------- main

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd")
    s = sub.add_parser("submit", help="validate and publish a job file")
    s.add_argument("job")
    s.add_argument("--commit", metavar="REV", help="pin this commit as the job's git input")
    s.add_argument("--via", choices=("bundle", "hub"), default="bundle",
                   help="how an unpushed commit reaches the other machine (default bundle)")
    s.set_defaults(fn=cmd_submit)
    s = sub.add_parser("split", help="submit a frame range as parts either machine can take, plus an assembly")
    s.add_argument("template", help="job whose strings use {part_first} {part_last} {part_count} "
                                    "{part_start_s} {part_end_s}")
    s.add_argument("--frames", required=True, metavar="FIRST:END", help="frame numbers, END exclusive")
    s.add_argument("--part", type=int, default=48, help="frames per part (default 48)")
    s.add_argument("--fps", type=float, default=24.0, help="for the {part_*_s} times (default 24)")
    s.add_argument("--assemble", metavar="JOB.json", help="job to run once every part is done")
    s.add_argument("--commit", metavar="REV", help="pin this commit as every job's git input")
    s.add_argument("--via", choices=("bundle", "hub"), default="bundle")
    s.add_argument("--dry-run", action="store_true", help="list the jobs; submit nothing")
    s.set_defaults(fn=cmd_split)
    s = sub.add_parser("status", help="per machine: gate, running, ETA, queued")
    s.add_argument("--job")
    s.add_argument("--json", action="store_true")
    s.set_defaults(fn=cmd_status)
    s = sub.add_parser("cancel", help="withdraw a job (submitter or render coordinator)")
    s.add_argument("id")
    s.set_defaults(fn=cmd_cancel)
    s = sub.add_parser("gate", help="render coordinator only, on the owner's word")
    s.add_argument("machine", choices=MACHINES + ("both",))
    s.add_argument("state", choices=("run", "pause"))
    s.set_defaults(fn=cmd_gate)
    for name in ("hold", "release"):
        s = sub.add_parser(name, help="render coordinator only")
        s.add_argument("id")
        s.set_defaults(fn=cmd_hold)
    s = sub.add_parser("prioritize", help="render coordinator only: override a job's priority")
    s.add_argument("id")
    s.add_argument("priority", type=int)
    s.set_defaults(fn=cmd_prioritize)
    s = sub.add_parser("prefer", help="render coordinator only: route `any` jobs to one machine first (day) or off (night)")
    s.add_argument("machine", choices=MACHINES + ("off",))
    s.add_argument("--wait", type=int, default=1800, help="seconds a job waits for that machine (default 1800)")
    s.add_argument("--lanes", default="review,final", help="lanes it applies to (default review,final)")
    s.set_defaults(fn=cmd_prefer)
    s = sub.add_parser("install", help="install and start this machine's runner")
    s.add_argument("--repo", help="this machine's clone (default: the current repository)")
    s.add_argument("--blender", help="Blender executable for {blender}")
    s.add_argument("--tag", action="append", help="extra capability tag (repeatable)")
    s.add_argument("--mode", choices=("launchd", "nohup"), default="launchd",
                   help="macOS only; launchd needs Full Disk Access for Python.app, nohup is the fallback")
    s.add_argument("--no-service", action="store_true", help="copy files only")
    s.set_defaults(fn=cmd_install)
    s = sub.add_parser("runner")
    s.add_argument("--detached", action="store_true", help=argparse.SUPPRESS)
    s.set_defaults(fn=cmd_runner)
    s = sub.add_parser("wrap")
    s.add_argument("run_dir")
    s.set_defaults(fn=cmd_wrap)
    args = ap.parse_args(argv)
    if not getattr(args, "fn", None):
        ap.print_help()
        return 2
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
