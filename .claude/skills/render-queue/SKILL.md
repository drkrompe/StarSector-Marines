---
name: render-queue
description: Submit Blender renders (review stills, light daytime takes, full finals) to the render queue shared by the Windows workstation and the MacBook Pro, then check on them and collect the output. Use whenever a session needs a render that should not run inline, wants the other machine to render, or asks whether a queued render has finished. Also the reference for the render coordinator role (gates, holds, priorities).
---

# Render queue

Every Blender render in this project goes through one queue on Google Drive,
worked by a runner on each machine. **Do not start Blender renders from your own
shell**: the runners keep exactly one GPU job per machine, swap jobs in and out,
and keep the machines usable for everything else. A render you start yourself
fights a queued one for the GPU.

```bash
python tools/render_queue.py submit job.json --commit HEAD   # publish a job
python tools/render_queue.py status                          # whole picture, one call
python tools/render_queue.py status --job <id>               # one job and its result
python tools/render_queue.py cancel <id>                     # withdraw your own job
```

`status --json` gives the same view as data.

The queue folder is `G:\My Drive\starsector\render-queue\` on Windows and
`~/Library/CloudStorage/GoogleDrive-*/My Drive/starsector/render-queue/` on the Mac.

## Roles

- **Any session** submits jobs, reads `status`, and cancels its own jobs.
  It never changes a gate, never touches another session's job, and never
  edits `config.json`.
- **The render coordinator** owns the gates and the queue order. This is a
  role, not a particular session: the owner says which chat holds it. It
  flips a machine's gate only on the owner's word. It may hold, release,
  re-prioritize or cancel any job:

  ```bash
  python tools/render_queue.py gate mac run        # windows | mac | both, run | pause
  python tools/render_queue.py hold <id>           # keep back (swaps it out if running)
  python tools/render_queue.py release <id>
  python tools/render_queue.py prioritize <id> 10  # overrides the job's own priority
  ```

## Lanes and the gate

| lane | for | gate |
|------|-----|------|
| `review` | stills and takes the owner will look at | waits for `run` |
| `light` | half size, at most 16 samples, or a low-fps low-res clip (owner rule) | **runs while paused** |
| `final` | full-quality finals the owner approved | waits for `run` |

Within a lane, jobs run by priority (higher first), then submission time.
Review and light jobs run ahead of finals. When one arrives while a final is
rendering, the runner **swaps the final out**: it waits for the frame in
progress to land (up to 7 minutes), stops the final cleanly, runs the short
job, then relaunches the final. Closing the gate swaps a gated job out the
same way, with nothing swapped in. So a final's render command **must skip
frames that already exist**, as Blender does with `use_overwrite = False` /
`use_placeholder = True`. The runner deletes 0-byte placeholders before each
launch.

Respect the light-lane rule: a light job runs on the owner's GPU during the
day. One that runs past 30 minutes is stopped and marked failed.

Jobs run at low CPU priority (Windows below-normal, Mac nice 10).

## A job

```json
{
  "id": "hermes-pass3-hero-2026-10-20",
  "machine": "any",
  "lane": "light",
  "priority": 0,
  "after": [],
  "requires": ["gpu"],
  "cwd": "{checkout}/art-source/company-prologue/hermes-cinematic",
  "command": ["{blender}", "-b", "--factory-startup", "--python-exit-code", "1",
              "--python", "review_ship.py", "--", "--out", "{work}/frames", "--half", "--samples", "16"],
  "env": {},
  "inputs": [
    {"kind": "git", "commit": "<filled in by --commit>"},
    {"kind": "drive", "from": "{drive}/builds/broadsword/df72dd899",
     "to": "{checkout}/build/ships/broadsword",
     "manifest": "{checkout}/art-source/company-prologue/broadsword-cinematic/manifest.json"}
  ],
  "frames": {"dir": "{work}/frames", "glob": "*.png", "count": 2},
  "outputs": [{"from": "{work}/frames", "glob": "*.png",
               "to": "{drive}/review/hermes/2026-10-20_pass3-hero_{machine}"}]
}
```

- `id`: unique, lowercase `[a-z0-9._-]`. A job file is written once and never
  edited. To change a job, cancel it and submit a new id.
- `machine`: `windows`, `mac` or `any`. An `any` job goes to whichever
  machine claims it first; an idle machine gets two minutes' head start over
  a busy one. Once claimed, it belongs to that machine.
- `requires`: capability tags the machine must have. `status` lists each
  machine's tags. Windows has the RTX 3080 (`gpu`, `cuda`, `optix`,
  `gpu-rtx-3080`), `ffmpeg`, `starsector` (the game install) and
  `font-bahnschrift`. The Mac has 18 CPU cores and an M5 Max GPU (`gpu`,
  `metal`, `apple-silicon`, `cpu18`) and `ffmpeg`.
- `after`: job ids that must have finished `ok` first.
- `command` is an argv list. If it differs per machine, use
  `{"windows": [...], "mac": [...]}`. Placeholders work in `command`, `cwd`,
  `env`, `inputs`, `frames` and `outputs`:
  - `{checkout}`: the job's git checkout
  - `{work}`: a local scratch folder for the job, and the default `cwd`
  - `{drive}`: the Drive `starsector` folder
  - `{queue}`, `{home}`, `{repo}` (the machine's own clone), `{blender}`, `{python}`, `{ffmpeg}`, `{machine}`, `{job}`
- **Inputs.** A `git` input becomes `{checkout}`, a throwaway detached
  worktree at that exact commit. `--commit <rev>` pins it:
  - On `origin/main`, nothing needs to travel.
  - For a job pinned to the machine you submit from, nothing needs to travel either: the runner uses the same clone.
  - Otherwise the commit is carried as a git bundle on Drive (`--via bundle`, the default).

  Commit your work first; a WIP commit is fine. A `drive` input copies a
  build local before launch. Large untracked files such as built `.blend`
  files travel this way, from Drive `builds/<asset>/<commit>/`. With
  `manifest` (a committed `manifest.json` that has `asset`/`asset_sha256`),
  or an explicit `"sha256": {"rel/path": "<hex>"}`, the runner refuses a
  build that does not match.
- **Outputs.** Render into a local folder (`{work}/...`), never straight into
  Drive: placeholders would sync, and frame-skip resume must read a local
  folder. `outputs` are copied, never moved, to `to`. Finished frames are
  copied as they land; the rest is copied at completion. Without `to`, output
  lands in `render-queue/out/<id>/<machine>/`. Finals go to Drive
  `builds/<shot>-frames/<commit>/`. Review sets go to a run folder under
  `review/<asset>/`, with `{machine}` in its name (see CLAUDE.md); write its
  `NOTES.md` yourself once the job is done.
- `frames`: completion is decided by counting non-empty files matching `glob`
  in `dir`. The exit status does not decide it. Without `frames`, exit status
  0 is success.
- `keep_checkout: true` keeps the worktree after success; `low_priority:
  false` runs the job at normal priority.

## Checking and collecting

`status` prints a block for each machine:
- the gate and the runner's heartbeat
- its tags
- the running job, with frames done, seconds per frame and ETA, or "swapping out" while it waits for a frame to land
- any swapped-out jobs
- that machine's queue in running order, marked "gate closed" where the gate holds a job back

Below the machine blocks: unclaimed `any` jobs, blocked jobs (held, or waiting
on a dependency), and everything that finished in the last day.

`status --job <id>` prints the job's result file:
- `ok`, `failed` or `cancelled`
- the reason (frame count and exit status)
- elapsed time
- the copied outputs
- the last 40 log lines

The full job log is `render-queue/log/jobs/<id>.<machine>.log`.

A job does not report back to your session. Check `status` when you next need
the result, or tell the owner the job id and where its output will land.

## Runners

Install or update with `python tools/render_queue.py install`, run from a
checkout. It copies the script to a local folder and restarts the runner. A
running job survives a runner restart.

**Windows.** The runner lives in `%LOCALAPPDATA%\starsector-render-queue\`.
The scheduled task "Starsector render queue" starts it at logon, hidden.

**Mac.** The runner lives in `~/render-queue/` and runs under nohup, started
from ssh or Terminal. A LaunchAgent cannot list `~/Library/CloudStorage`
(macOS answers "Operation not permitted") without Full Disk Access, which is
the owner's call. So **after a Mac reboot, start the runner again**:

```bash
C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local dillon@192.168.1.97 "cd ~/render-queue && /usr/bin/python3 render_queue.py install"
```

A stale Mac heartbeat in `status` means the runner is down.

`config.json` belongs to the render coordinator. Besides gates, holds and
priority overrides it holds:
- `preempt`
- `swap_at_frame_boundary` and `swap_wait_max_seconds`
- `mac_swap`: `stop` (the default, which frees memory) or `freeze` (SIGSTOP)
- `low_priority`
- `light_max_seconds`
- `any_grace_seconds`
- `block_on_foreign_blender`: a runner starts nothing while a background Blender it did not start is rendering
- `poll_seconds` and `claim_wait_seconds`
- a reserved `schedule` slot for a later night window

The gate stays manual.
