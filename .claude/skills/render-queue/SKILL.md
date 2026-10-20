---
name: render-queue
description: Submit Blender renders (review stills, low-fps or low-resolution motion takes, full finals) to the render queue shared by the Windows workstation and the MacBook Pro, then check on them and collect the output. Use whenever a session needs a render that should not run inline, wants the Mac to render, or asks whether a queued render has finished.
---

# Render queue

Renders do not run from a session's own shell any more. A session writes a job
file to the queue on Google Drive, and a runner on each machine works through
the jobs **only while the owner has that machine's gate set to `run`**. The GPUs
are the owner's during the day, so expect a job to wait until the owner opens
the gate (over lunch, overnight). Never flip a gate yourself; only the
coordinator does, and only on the owner's word.

Everything goes through `tools/render_queue.py` (Python 3, standard library):

```bash
python tools/render_queue.py submit job.json [--bundle <commit>]
python tools/render_queue.py status            # gates, runners, recent jobs
python tools/render_queue.py status --job <id> # one job, with its result
python tools/render_queue.py cancel <id>
```

Queue folder: `G:\My Drive\starsector\render-queue\` (Windows),
`~/Library/CloudStorage/GoogleDrive-*/My Drive/starsector/render-queue/` (Mac).

## A job

```json
{
  "id": "second-wind-pass7-hero-2026-10-20",
  "machine": "any",
  "lane": "review",
  "priority": 0,
  "after": [],
  "cwd": "{checkout}/art-source/company-prologue/second-wind-cinematic",
  "command": ["{blender}", "-b", "--factory-startup", "--python-exit-code", "1",
              "--python", "review_ship.py", "--", "--out", "{work}/frames", "--half"],
  "env": {"CINE_SCALE": "0.5"},
  "inputs": [
    {"kind": "git", "commit": "<filled in by --bundle>"},
    {"kind": "drive", "from": "{drive}/builds/broadsword/df72dd899",
     "to": "{checkout}/build/ships/broadsword",
     "manifest": "{checkout}/art-source/company-prologue/broadsword-cinematic/manifest.json"}
  ],
  "frames": {"dir": "{work}/frames", "glob": "*.png", "count": 2},
  "outputs": [{"from": "{work}/frames", "glob": "*.png",
               "to": "{drive}/review/second-wind/2026-10-20_pass7-hero_{machine}"}]
}
```

- `id`: unique, lowercase, `[a-z0-9._-]`. A job file is written once and never
  edited. To change a job, cancel it and submit a new id.
- `machine`: `windows`, `mac` or `any`. An `any` job is claimed by whichever
  runner gets to it first and then belongs to that machine.
- `lane`: `review` (stills, light motion takes) always runs before `final`. A
  runnable review job suspends a running final; the final picks up afterwards.
  `priority` (higher first) orders jobs within a lane, then submission time.
- `after`: job ids that must have finished `ok` first.
- `command` is an argv list. For a command that differs per machine use
  `{"windows": [...], "mac": [...]}`. Placeholders work in `command`, `cwd`,
  `env`, `inputs`, `frames` and `outputs`:
  `{checkout}` (the job's git checkout), `{work}` (a local scratch folder for
  the job, the default `cwd`), `{drive}` (the Drive `starsector` folder),
  `{queue}`, `{home}`, `{repo}` (the machine's own clone), `{blender}`,
  `{python}`, `{machine}`, `{job}`.
- `inputs`: a `git` input becomes `{checkout}`, a detached worktree of that
  machine's clone. **Session branches are never pushed**, so a commit that is
  not on `origin/main` must travel as a bundle: pass `--bundle <commit>` and
  `submit` writes `inputs/<id>.bundle` (`<commit> ^origin/main`) and fills in
  the input. A `drive` input copies a file or folder local; with `manifest`
  (a committed `manifest.json` with `asset`/`asset_sha256`) or an explicit
  `"sha256": {"rel/path": "<hex>"}` it refuses a build that does not match.
- `frames`: completion is decided by counting non-empty files matching `glob`
  in `dir`, not by the exit status. 0-byte placeholders are removed before each
  launch, so a render that skips existing frames resumes cleanly. Without
  `frames`, exit status 0 is success.
- `outputs`: copied when the job succeeds. Without `to` they land in
  `render-queue/out/<id>/<machine>/`. For an owner review set use a run folder
  under `review/<asset>/` per CLAUDE.md, with `{machine}` in the name, and
  write its `NOTES.md` yourself once the job is done.
- `keep_checkout: true` keeps the job's worktree after success; otherwise it
  is removed once its outputs are copied.

Follow the compute rules you were given: a look question is one or two views at
half size and low samples; full quality only for finals the owner approved.

## Checking and collecting

`status` shows each machine's gate and runner heartbeat, then every job from
the last day: `queued`, `waiting for <dep>`, `claimed by <machine>`,
`running on <machine> (n/N frames)`, `stopped on <machine>` (paused or
preempted), or the result per machine. `status --job <id>` prints the result
file: `ok`/`failed`/`cancelled`, the reason (frame count and exit status),
elapsed time, the copied outputs, and the last 40 log lines. The full job log
is `render-queue/log/jobs/<id>.<machine>.log`; each runner's own log is
`render-queue/log/<machine>.log`.

A job does not report back to your session. Check `status` when you next need
the result, or tell the owner the job id and where the output will land.

## Gates (coordinator only, on the owner's word)

```bash
python tools/render_queue.py gate mac run      # or windows / both, run / pause
```

`pause` stops work at once: on Windows the job's process tree is killed and
restarted later (finished frames are kept, so use renders that skip existing
frames); on the Mac the job is frozen with SIGSTOP and resumed with SIGCONT.
`config.json` also holds `preempt`, `poll_seconds`, `claim_wait_seconds` and a
reserved `schedule` slot for a later night window; the gate is manual for now.

## Runners

Installed with `python tools/render_queue.py install` from a checkout (re-run it
after changing `tools/render_queue.py`; it copies the script local and restarts
the runner):

- Windows: `%LOCALAPPDATA%\starsector-render-queue\`, started at logon by the
  scheduled task "Starsector render queue" under `pythonw` (hidden). Jobs break
  away from the task's job object, so restarting the runner leaves them running.
- Mac: `~/render-queue/`, started under `nohup` from an ssh or Terminal
  session (`python3 tools/render_queue.py install --repo ~/workspace/StarSector-Marines`).
  A LaunchAgent cannot list `~/Library/CloudStorage` (macOS privacy rules
  answer "Operation not permitted") unless Python is given Full Disk Access,
  which is the owner's call, so **after a Mac reboot the runner must be started
  again** by re-running that install over ssh:
  `C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local dillon@192.168.1.97 "cd ~/render-queue && /usr/bin/python3 render_queue.py install"`.
  `status` shows a stale Mac heartbeat when it is down.

Each runner keeps `machine.json` (Blender path, clone, Drive root) and
`state.json` locally, and writes only its own `status/`, `done/`, `claims/` and
`log/` files on Drive. `any` claims wait `claim_wait_seconds` for Drive to sync
the other machine's claim; the earlier claim wins.
