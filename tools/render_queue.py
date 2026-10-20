#!/usr/bin/env python3
"""Render queue shared by the Windows workstation and the MacBook Pro.

The queue lives on Google Drive (``starsector/render-queue/``). Every file in it
has exactly one writer, so Drive never sees two machines editing one file:

    config.json              coordinator only: per-machine run/pause gate
    jobs/<id>.json           the submitting session, written once, never edited
    inputs/<id>.bundle       the submitting session (git bundle of a session commit)
    cancel/<id>              the submitting session, to withdraw a job
    claims/<id>.<machine>    that machine's runner (``any`` jobs only)
    done/<id>.<machine>      that machine's runner: the job's result
    status/<machine>.json    that machine's runner: heartbeat and current job
    log/<machine>.log        that machine's runner
    out/<id>/<machine>/      default output copy destination

A runner per machine is installed to a local folder (never run from Drive) and
polls the queue. It launches each job through its own wrapper process, which
records the exit status, so a runner restart neither loses nor orphans a job.
Completion is decided by the job's expected-frames check where one is given.

Commands (``python tools/render_queue.py <command> -h`` for options):

    submit  JOB.json [--bundle COMMIT]   validate and publish a job
    status  [--job ID]                   queue, gates, heartbeats, results
    cancel  ID                           withdraw a job
    gate    MACHINE run|pause            coordinator only, on the owner's word
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
import platform
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import traceback
from pathlib import Path

VERSION = "2026-10-20.1"
MACHINES = ("mac", "windows")
DEFAULT_CONFIG = {
    "gates": {"windows": "pause", "mac": "pause"},
    # Order of lanes: an earlier lane always runs first.
    "lanes": ["review", "final"],
    # A runnable review job suspends a running final (Windows: kill and restart
    # later, finished frames are kept; Mac: SIGSTOP/SIGCONT).
    "preempt": True,
    "poll_seconds": 20,
    # How long an `any` claim waits for Drive to sync the other machine's claim.
    "claim_wait_seconds": 90,
    # Reserved for later; not read yet. The gate is manual for now.
    "schedule": None,
}
IS_WINDOWS = os.name == "nt"
ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,80}$")


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


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd, cwd=None, check=True, timeout=None) -> subprocess.CompletedProcess:
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = 0x08000000  # CREATE_NO_WINDOW
    return subprocess.run([str(c) for c in cmd], cwd=cwd, check=check, timeout=timeout,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          universal_newlines=True, **kw)


class Queue:
    def __init__(self, root: Path):
        self.root = root

    def d(self, name: str) -> Path:
        p = self.root / name
        p.mkdir(parents=True, exist_ok=True)
        return p

    def config(self) -> dict:
        cfg = dict(DEFAULT_CONFIG)
        cfg.update(read_json(self.root / "config.json", {}) or {})
        return cfg

    def jobs(self) -> dict:
        out = {}
        for p in sorted(self.d("jobs").glob("*.json")):
            job = read_json(p)
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
                if isinstance(res, dict):
                    out.setdefault(job_id, {})[machine] = res
        return out

    def claims(self, job_id: str) -> dict:
        out = {}
        for m in MACHINES:
            c = read_json(self.d("claims") / f"{job_id}.{m}")
            if isinstance(c, dict):
                out[m] = c
        return out

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


# ---------------------------------------------------------------- job schema

def validate_job(job: dict) -> list:
    errs = []
    jid = job.get("id")
    if not isinstance(jid, str) or not ID_RE.match(jid):
        errs.append("id: lowercase letters, digits, '.', '_' or '-', 3-81 chars")
    if job.get("machine") not in ("windows", "mac", "any"):
        errs.append("machine: windows | mac | any")
    if job.get("lane") not in ("review", "final"):
        errs.append("lane: review | final")
    if not isinstance(job.get("priority", 0), int):
        errs.append("priority: integer, higher runs first within a lane")
    if not isinstance(job.get("after", []), list):
        errs.append("after: list of job ids")
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
            if not re.match(r"^[0-9a-f]{7,40}$", str(inp.get("commit", ""))):
                errs.append(f"inputs[{i}]: git input needs a commit id")
        elif kind == "drive":
            if not inp.get("from") or not inp.get("to"):
                errs.append(f"inputs[{i}]: drive input needs from and to")
        else:
            errs.append(f"inputs[{i}]: kind is git | drive")
    git_inputs = [i for i in job.get("inputs", []) if i.get("kind") == "git"]
    if len(git_inputs) > 1:
        errs.append("inputs: at most one git input (it becomes {checkout})")
    fr = job.get("frames")
    if fr is not None and not (isinstance(fr, dict) and fr.get("dir") and isinstance(fr.get("count"), int)):
        errs.append("frames: {dir, glob, count}")
    for o in job.get("outputs", []):
        if not o.get("from"):
            errs.append("outputs: each needs from (and optional glob, to)")
    return errs


# ---------------------------------------------------------------- submit / status / gate / cancel

def repo_root() -> Path:
    out = run(["git", "rev-parse", "--show-toplevel"]).stdout.strip()
    return Path(out)


def cmd_submit(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    job = read_json(Path(args.job))
    if not isinstance(job, dict):
        raise SystemExit(f"{args.job}: not a JSON object")
    job.setdefault("priority", 0)
    job.setdefault("after", [])
    job.setdefault("inputs", [])
    job.setdefault("env", {})
    job["submitted"] = now_iso()
    job.setdefault("submitted_by", f"{getpass.getuser()}@{socket.gethostname()}")
    if args.bundle:
        commit = run(["git", "rev-parse", args.bundle + "^{commit}"]).stdout.strip()
        git_in = [i for i in job["inputs"] if i.get("kind") == "git"]
        if not git_in:
            git_in = [{"kind": "git"}]
            job["inputs"].insert(0, git_in[0])
        git_in[0]["commit"] = commit
        on_main = run(["git", "merge-base", "--is-ancestor", commit, "origin/main"], check=False).returncode == 0
        if on_main:
            print(f"{commit[:9]} is on origin/main; no bundle needed")
        else:
            ref = f"refs/render-queue/{job.get('id')}"
            bundle = q.d("inputs") / f"{job.get('id')}.bundle"
            if bundle.exists():
                raise SystemExit(f"{bundle} already exists")
            run(["git", "update-ref", ref, commit])
            try:
                run(["git", "bundle", "create", str(bundle), ref, "^origin/main"])
            finally:
                run(["git", "update-ref", "-d", ref], check=False)
            git_in[0]["bundle"] = f"inputs/{bundle.name}"
            git_in[0]["bundle_ref"] = ref
            print(f"bundled {commit[:9]} ^origin/main -> {bundle} ({bundle.stat().st_size} bytes)")
    errs = validate_job(job)
    if errs:
        raise SystemExit("invalid job:\n  " + "\n  ".join(errs))
    write_once(q.d("jobs") / f"{job['id']}.json", job)
    print(f"submitted {job['id']} ({job['machine']}, {job['lane']}) -> {q.root / 'jobs'}")
    return 0


def describe_state(q: Queue, job: dict, done: dict, statuses: dict) -> str:
    jid = job["id"]
    if jid in done:
        return ", ".join(f"{m}:{r.get('result')}" for m, r in sorted(done[jid].items()))
    if q.cancelled(jid):
        return "cancelled (not started)"
    for m, st in statuses.items():
        for slot in [st.get("current")] + list(st.get("suspended", [])):
            if slot and slot.get("id") == jid:
                prog = slot.get("progress")
                return f"{slot.get('state')} on {m}" + (f" ({prog})" if prog else "")
    missing = [a for a in job.get("after", []) if not job_succeeded(done.get(a, {}))]
    if missing:
        return "waiting for " + ", ".join(missing)
    if job["machine"] == "any":
        w = claim_winner(q.claims(jid))
        if w:
            return f"claimed by {w}"
    return "queued"


def cmd_status(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    cfg = q.config()
    done = q.done()
    statuses = {m: read_json(q.d("status") / f"{m}.json", {}) or {} for m in MACHINES}
    if args.job:
        job = q.jobs().get(args.job)
        if not job:
            raise SystemExit(f"no job {args.job}")
        print(json.dumps(job, indent=2))
        print("state:", describe_state(q, job, done, statuses))
        for m, r in sorted(done.get(args.job, {}).items()):
            print(f"--- done on {m}")
            print(json.dumps(r, indent=2))
        return 0
    print(f"queue: {q.root}")
    for m in MACHINES:
        st = statuses[m]
        age = ""
        if st.get("updated_epoch"):
            age = f", heartbeat {int(time.time() - st['updated_epoch'])}s ago"
        cur = st.get("current") or {}
        cur_s = f", current {cur.get('id')} {cur.get('state')} {cur.get('progress') or ''}" if cur else ""
        print(f"  {m:8s} gate={cfg['gates'].get(m)} runner={st.get('runner', 'never seen')}{age}{cur_s}")
    jobs = sorted(q.jobs().values(), key=lambda j: j.get("submitted", ""))
    if not args.all:
        jobs = [j for j in jobs if j["id"] not in done or
                (time.time() - max(r.get("finished_epoch", 0) for r in done[j["id"]].values())) < 86400]
    for j in jobs:
        print(f"  {j['id']:40s} {j['machine']:7s} {j['lane']:6s} p{j.get('priority', 0):<3d} "
              f"{describe_state(q, j, done, statuses)}")
    return 0


def cmd_gate(args) -> int:
    q = Queue(find_drive_root() / "render-queue")
    cfg = read_json(q.root / "config.json", None) or dict(DEFAULT_CONFIG)
    machines = MACHINES if args.machine == "both" else (args.machine,)
    for m in machines:
        cfg.setdefault("gates", {})[m] = args.state
    write_json(q.root / "config.json", cfg)
    print("gates:", cfg["gates"])
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

def proc_alive(pid: int) -> bool:
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
    # A zombie still answers kill(0); ask ps.
    r = subprocess.run(["ps", "-o", "stat=", "-p", str(pid)], stdout=subprocess.PIPE, universal_newlines=True)
    return bool(r.stdout.strip()) and not r.stdout.strip().startswith("Z")


def kill_tree(pid: int) -> None:
    if not pid:
        return
    if IS_WINDOWS:
        run(["taskkill", "/T", "/F", "/PID", str(pid)], check=False)
    else:
        for sig in (signal.SIGCONT, signal.SIGTERM):
            try:
                os.killpg(int(pid), sig)
            except (ProcessLookupError, PermissionError):
                pass
        time.sleep(3)
        try:
            os.killpg(int(pid), signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass


def signal_group(pgid: int, sig) -> None:
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
    run_dir = Path(args.run_dir)
    launch = read_json(run_dir / "launch.json")
    log = open(run_dir / "job.log", "ab")
    log.write(f"\n==== {now_iso()} launch {launch['argv']}\n".encode())
    log.flush()
    env = dict(os.environ)
    env.update(launch.get("env", {}))
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = 0x08000000
    else:
        kw["start_new_session"] = True
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
        self.awake = None
        self.last_status = None
        self.last_status_time = 0.0

    # -- bookkeeping
    def save(self):
        write_json(self.state_path, self.state)

    def log(self, msg):
        self.q.log(self.machine, msg)

    def placeholders(self, job_id: str, rec: dict) -> dict:
        p = {
            "drive": str(self.drive),
            "queue": str(self.q.root),
            "home": str(Path.home()),
            "repo": self.settings.get("repo", ""),
            "blender": self.settings.get("blender", "blender"),
            "python": self.settings.get("python", python_console()),
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
        self.log(f"runner {VERSION} started pid {os.getpid()} python {sys.version.split()[0]}")
        while True:
            cfg = self.q.config()
            try:
                self.tick(cfg)
            except Exception:
                self.log("tick error: " + traceback.format_exc().replace("\n", " | "))
            time.sleep(max(5, int(cfg.get("poll_seconds", 20))))

    def tick(self, cfg: dict):
        gate = cfg.get("gates", {}).get(self.machine, "pause")
        jobs = self.q.jobs()
        done = self.q.done()
        self.reap(jobs, done)
        self.handle_cancels(jobs)
        done = self.q.done()  # reaping may have just written a result
        running = self.running_id()
        if gate != "run":
            if running:
                self.suspend(running, "paused")
            self.set_awake(False)
            self.publish(cfg, gate, jobs)
            return
        if running:
            # Only a review job may interrupt a final; anything else waits its turn,
            # and an `any` job is not claimed while this machine is busy.
            cur = self.state["active"][running]
            if cfg.get("preempt", True) and cur.get("lane") == "final":
                best = self.pick(cfg, jobs, done, only_lane="review")
                if best:
                    self.suspend(running, "preempted")
                    self.start_or_resume(best, jobs[best])
        else:
            best = self.pick(cfg, jobs, done)
            if best:
                self.start_or_resume(best, jobs[best])
        self.set_awake(self.running_id() is not None)
        self.publish(cfg, gate, jobs)

    def running_id(self):
        for jid, rec in self.state["active"].items():
            if rec.get("state") == "running":
                return jid
        return None

    def pick(self, cfg, jobs, done, only_lane=None):
        lanes = cfg.get("lanes", ["review", "final"])
        cands = []
        for jid, job in jobs.items():
            if job.get("machine") not in (self.machine, "any"):
                continue
            if only_lane and job.get("lane") != only_lane:
                continue
            if self.state["active"].get(jid, {}).get("state") == "running":
                continue
            if jid in done and (self.machine in done[jid] or job_succeeded(done[jid])):
                continue
            if self.q.cancelled(jid):
                continue
            if any(not job_succeeded(done.get(a, {})) for a in job.get("after", [])):
                continue
            mine = jid in self.state["active"]
            if job["machine"] == "any" and not mine:
                w = claim_winner(self.q.claims(jid))
                if w and w != self.machine:
                    continue
            lane = lanes.index(job["lane"]) if job["lane"] in lanes else len(lanes)
            cands.append((lane, 0 if mine else 1, -int(job.get("priority", 0)), job.get("submitted", ""), jid))
        for c in sorted(cands):
            jid = c[-1]
            if jobs[jid]["machine"] == "any" and jid not in self.state["active"]:
                if not self.claim(jid, cfg):
                    continue
            return jid
        return None

    def claim(self, jid, cfg) -> bool:
        path = self.q.d("claims") / f"{jid}.{self.machine}"
        if not path.exists():
            write_json(path, {"machine": self.machine, "time": time.time(), "at": now_iso()})
            wait = int(cfg.get("claim_wait_seconds", 90))
            self.log(f"claimed {jid}; waiting {wait}s for the other machine's claim to sync")
            self.publish(cfg, "run", None, note=f"claiming {jid}")
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
        if rec and rec.get("state") == "stopped" and not IS_WINDOWS and proc_alive(rec.get("pgid")):
            signal_group(rec["pgid"], signal.SIGCONT)
            rec["state"] = "running"
            self.save()
            self.log(f"resumed {jid} (SIGCONT)")
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
        self.launch(jid, job, rec)

    def prepare(self, jid, job, rec):
        work = self.local / "work" / jid
        work.mkdir(parents=True, exist_ok=True)
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
        run(["git", "-C", repo, "fetch", "--quiet", "origin"], check=False, timeout=600)
        if inp.get("bundle"):
            bundle = self.q.root / inp["bundle"]
            ref = inp.get("bundle_ref") or f"refs/render-queue/{jid}"
            v = run(["git", "-C", repo, "bundle", "verify", bundle], check=False)
            if v.returncode != 0:
                raise RuntimeError("bundle does not verify against this clone: " + v.stdout[-400:])
            run(["git", "-C", repo, "fetch", "--quiet", bundle, f"{ref}:refs/render-queue/{jid}"], timeout=600)
        if run(["git", "-C", repo, "cat-file", "-e", commit + "^{commit}"], check=False).returncode != 0:
            raise RuntimeError(f"commit {commit} is not on origin/main here and the job carries no bundle")
        dest = repo / ".claude" / "worktrees" / f"rq-{jid}"
        if dest.exists():
            head = run(["git", "-C", dest, "rev-parse", "HEAD"], check=False).stdout.strip()
            if head.startswith(commit) or commit.startswith(head[:len(commit)]):
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
        run_dir = self.local / "run" / jid
        run_dir.mkdir(parents=True, exist_ok=True)
        for stale in ("exit.json", "pid.json"):
            (run_dir / stale).unlink(missing_ok=True)
        write_json(run_dir / "launch.json", {"argv": argv, "cwd": cwd, "env": env})
        installed = self.local / "render_queue.py"
        script = installed if installed.exists() else Path(__file__).resolve()
        p = spawn_detached([python_gui(), str(script), "wrap", str(run_dir)], run_dir / "wrapper.log")
        rec.update(state="running", wrapper=p.pid, attempts=rec.get("attempts", 0) + 1,
                   started=now_iso(), started_epoch=time.time(), run_dir=str(run_dir))
        rec.pop("pgid", None)
        self.save()
        shown = " ".join(argv).replace("\n", "\\n")
        self.log(f"launched {jid} attempt {rec['attempts']}: {shown[:300]}")

    def child_info(self, rec):
        pid = read_json(Path(rec["run_dir"]) / "pid.json", {}) or {}
        return pid.get("child")

    def suspend(self, jid, why):
        rec = self.state["active"][jid]
        if IS_WINDOWS:
            kill_tree(rec.get("wrapper"))
            rec["state"] = "stopped"
            self.log(f"{why}: killed {jid} (restarts later; finished frames kept)")
        else:
            child = self.child_info(rec)
            if child:
                rec["pgid"] = child
                signal_group(child, signal.SIGSTOP)
            rec["state"] = "stopped"
            self.log(f"{why}: SIGSTOP {jid}")
        rec["why"] = why
        self.save()

    def reap(self, jobs, done):
        for jid, rec in list(self.state["active"].items()):
            job = jobs.get(jid)
            if job is None:
                self.log(f"job file for {jid} vanished; dropping local record")
                del self.state["active"][jid]
                self.save()
                continue
            if rec.get("state") != "running":
                continue
            run_dir = Path(rec["run_dir"])
            ex = read_json(run_dir / "exit.json")
            if ex is not None:
                self.complete(jid, job, rec, ex)
            elif not proc_alive(rec.get("wrapper")) and time.time() - rec.get("started_epoch", 0) > 15:
                # Wrapper gone without recording an exit: reboot, kill from outside.
                if rec.get("attempts", 0) >= 3:
                    self.finish(jid, job, rec, "failed", reason="process vanished three times")
                else:
                    self.log(f"{jid}: wrapper vanished without an exit record; relaunching")
                    self.launch(jid, job, rec)

    def complete(self, jid, job, rec, ex):
        ph = self.placeholders(jid, rec)
        fr = self.expand(job.get("frames"), ph)
        code = ex.get("exit_code")
        if fr:
            have = len([p for p in self.frame_files(fr) if p.stat().st_size > 0])
            ok = have >= int(fr["count"])
            reason = f"{have}/{fr['count']} frames, exit {code}"
        else:
            ok = code == 0
            reason = f"exit {code}" + (f" ({ex.get('error')})" if ex.get("error") else "")
        self.finish(jid, job, rec, "ok" if ok else "failed", reason=reason, exit_code=code)

    def copy_outputs(self, jid, job, rec) -> list:
        ph = self.placeholders(jid, rec)
        copied = []
        for o in self.expand(job.get("outputs", []), ph):
            src = Path(o["from"])
            dst = Path(o.get("to") or str(self.q.root / "out" / jid / self.machine))
            files = [src] if src.is_file() else sorted(
                p for p in src.glob(o.get("glob", "*")) if p.is_file()) if src.is_dir() else []
            for f in files:
                if f.stat().st_size == 0:
                    continue
                dst.mkdir(parents=True, exist_ok=True)
                t = dst / f.name
                if not (t.exists() and t.stat().st_size == f.stat().st_size):
                    shutil.copy2(f, t)
                copied.append(str(t))
        return copied

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
        if result == "ok" and copied and rec.get("checkout") and not job.get("keep_checkout"):
            self.remove_checkout(jid, rec["checkout"])

    def remove_checkout(self, jid, checkout):
        repo = self.settings.get("repo")
        r = run(["git", "-C", repo, "worktree", "remove", "--force", checkout], check=False)
        run(["git", "-C", repo, "update-ref", "-d", f"refs/render-queue/{jid}"], check=False)
        if r.returncode != 0:
            self.log(f"could not remove checkout {checkout}: {r.stdout.strip()[:200]}")

    def handle_cancels(self, jobs):
        for jid, rec in list(self.state["active"].items()):
            if self.q.cancelled(jid):
                if IS_WINDOWS:
                    kill_tree(rec.get("wrapper"))
                else:
                    child = self.child_info(rec) if rec.get("run_dir") else None
                    if child:
                        kill_tree(child)
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
    def progress(self, jid, job, rec):
        fr = self.expand(job.get("frames"), self.placeholders(jid, rec)) if job else None
        if not fr:
            return None
        have = len([p for p in self.frame_files(fr) if p.stat().st_size > 0])
        return f"{have}/{fr['count']} frames"

    def publish(self, cfg, gate, jobs, note=None):
        jobs = jobs if jobs is not None else self.q.jobs()
        cur, suspended = None, []
        for jid, rec in self.state["active"].items():
            slot = {"id": jid, "state": rec.get("state"), "lane": rec.get("lane"),
                    "attempts": rec.get("attempts"), "started": rec.get("started"),
                    "progress": self.progress(jid, jobs.get(jid), rec)}
            if rec.get("state") in ("running", "preparing"):
                cur = slot
            else:
                slot["why"] = rec.get("why")
                suspended.append(slot)
        body = {"machine": self.machine, "gate": gate, "current": cur, "suspended": suspended,
                "note": note, "runner": f"{VERSION} pid {os.getpid()}", "host": socket.gethostname()}
        # Write on change, and every five minutes as a heartbeat.
        if body != self.last_status or time.time() - self.last_status_time > 300:
            self.last_status = dict(body)
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


def cmd_runner(args) -> int:
    lock = single_instance(local_root())
    if lock is None:
        print("a runner is already running on this machine")
        return 0
    Runner().loop()
    return 0


# ---------------------------------------------------------------- install

TASK_NAME = "Starsector render queue"
PLIST_LABEL = "com.starsectormarines.renderqueue"


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
    settings.update({k: v for k, v in {
        "machine": this_machine(), "drive": str(drive), "python": python_console(),
        "repo": args.repo or settings.get("repo") or str(repo_root()),
        "blender": args.blender or settings.get("blender") or default_blender(),
    }.items() if v})
    write_json(local / "machine.json", settings)
    print("installed", local / "render_queue.py", "settings", json.dumps(settings))
    if args.no_service:
        return 0
    if IS_WINDOWS:
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
            f"Stop-ScheduledTask -TaskName '{TASK_NAME}' -ErrorAction SilentlyContinue;"
            f"Start-ScheduledTask -TaskName '{TASK_NAME}'"
        )
        r = run(["powershell", "-NoProfile", "-Command", ps], check=False)
        print(r.stdout.strip() or f"scheduled task '{TASK_NAME}' registered and started")
        return r.returncode
    if args.mode == "nohup":
        run(["/bin/zsh", "-c", f"nohup /usr/bin/python3 '{local / 'render_queue.py'}' runner "
                               f">> '{local / 'runner.out'}' 2>&1 &!"])
        print("runner started under nohup (does not survive a reboot)")
        return 0
    plist = Path.home() / "Library/LaunchAgents" / f"{PLIST_LABEL}.plist"
    plist.write_text(f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>{PLIST_LABEL}</string>
  <key>ProgramArguments</key><array>
    <string>/usr/bin/python3</string><string>{local / 'render_queue.py'}</string><string>runner</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Interactive</string>
  <key>StandardOutPath</key><string>{local / 'runner.out'}</string>
  <key>StandardErrorPath</key><string>{local / 'runner.out'}</string>
</dict></plist>
""", encoding="utf-8")
    uid = os.getuid()
    run(["launchctl", "bootout", f"gui/{uid}/{PLIST_LABEL}"], check=False)
    r = run(["launchctl", "bootstrap", f"gui/{uid}", str(plist)], check=False)
    print(r.stdout.strip() or f"launch agent {PLIST_LABEL} loaded")
    return r.returncode


def default_blender() -> str:
    if IS_WINDOWS:
        hits = sorted(glob.glob("C:/Program Files/Blender Foundation/Blender */blender.exe"))
        return hits[-1] if hits else "blender"
    return "/Applications/Blender.app/Contents/MacOS/Blender"


# ---------------------------------------------------------------- main

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd")
    s = sub.add_parser("submit", help="validate and publish a job file")
    s.add_argument("job")
    s.add_argument("--bundle", metavar="COMMIT",
                   help="carry this commit as a git bundle (needed unless it is on origin/main)")
    s.set_defaults(fn=cmd_submit)
    s = sub.add_parser("status", help="show gates, runners and jobs")
    s.add_argument("--job")
    s.add_argument("--all", action="store_true", help="include jobs finished over a day ago")
    s.set_defaults(fn=cmd_status)
    s = sub.add_parser("cancel", help="withdraw a job")
    s.add_argument("id")
    s.set_defaults(fn=cmd_cancel)
    s = sub.add_parser("gate", help="coordinator only, on the owner's word")
    s.add_argument("machine", choices=MACHINES + ("both",))
    s.add_argument("state", choices=("run", "pause"))
    s.set_defaults(fn=cmd_gate)
    s = sub.add_parser("install", help="install and start this machine's runner")
    s.add_argument("--repo", help="this machine's clone (default: the current repository)")
    s.add_argument("--blender", help="Blender executable for {blender}")
    s.add_argument("--mode", choices=("launchd", "nohup"), default="launchd", help="macOS only")
    s.add_argument("--no-service", action="store_true", help="copy files only")
    s.set_defaults(fn=cmd_install)
    s = sub.add_parser("runner")
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
