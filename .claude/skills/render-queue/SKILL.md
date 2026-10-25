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
  python tools/render_queue.py prefer mac          # by day: renders go to the Mac first
  python tools/render_queue.py prefer off          # at night: either machine, as before
  ```

  Windows is the owner's daily development machine; the Mac is the render box.
  With `prefer mac`, Windows leaves an unclaimed `any` job in the review and
  final lanes to the Mac until it has waited 30 minutes (`--wait`), unless the
  Mac's runner is down or its gate is paused. Light jobs and assemblies pinned
  to Windows are unaffected. The coordinator turns it on when the owner says
  good morning and off when they go to bed, alongside `preempt_min_run_seconds`.

### Holding the coordinator role

The standing practice, so a session that takes the role over (a new chat, or
the same work on another account) runs the queue the way the last one did.

- **Taking over.** Run `status`, read `config.json`, and read the hand-off
  note `render-queue/COORDINATOR.md` on Drive if the last coordinator left one:
  it holds what `status` cannot show (who submitted what, what the owner was
  asked, what is waiting on them). Then schedule a check-in about every half
  hour (`CronCreate`, at :13 and :43). Check-ins live only in the session that
  made them, so a new session or account starts without one.
- **A check-in** runs `status` and acts on what it finds: a failed job, a stale
  heartbeat (over 15 minutes), a stall, a light-job storm, a Blender the
  runners did not start. When nothing changed, the report is one line.
- **Day and night.** "Good morning": `prefer mac` and
  `preempt_min_run_seconds` 1200. "Going to bed": `prefer off` and 3600, so a
  final runs at least an hour before a short job can swap it out. The
  preempt value is a field in `config.json`; edit it there.
- **Windows is the owner's.** When they say it is under load, `gate windows
  pause`: the running final swaps out at its next frame and keeps its frames.
  Its Windows halves and every assembly wait until it opens again. Moving that
  work to the Mac means cancelling the Windows jobs and resubmitting them
  pinned to the Mac under new ids; it is only worth it when the Mac's own
  queue would otherwise run dry.
- **Assemblies run on Windows.** Drive on the Mac stalls on downloads, so a job
  that reads a whole frame folder off Drive is pinned to Windows. Restarting
  Drive on the Mac needs the owner's OK.
- **Light jobs stay short.** A light job is stopped at 30 minutes and marked
  failed; at the Mac's 40 s a frame that is about 36 frames. Ask a session
  that keeps hitting it to resubmit as `review` or cut the frames.
- **A Blender the runners did not start** holds the queue up
  (`block_on_foreign_blender`). Ask the session that started it to move it into
  the queue. One left behind by a session that has finished, the coordinator
  stops.
- **Failures** are reported once to the session that submitted the job (or its
  coordinator), with the reason from `status --job` and where its partial output
  is.
- **The owner's calls are pictures.** Anything visual the coordinator wants a
  decision on goes in front of the owner as renders, and finished previews go
  on the coordinator's own preview page (see CLAUDE.md's review rules).
  Artifact pages belong to the account that published them: after an account
  switch the new session cannot republish the old pages, so it starts its own
  and says so.

## Lanes and the gate

| lane | for | gate |
|------|-----|------|
| `light` | half size, at most 16 samples, or a low-fps low-res clip (owner rule) | **runs while paused** |
| `review` | stills and takes the owner will look at | waits for `run` |
| `final` | full-quality finals the owner approved | waits for `run` |

Lanes run in that order: light before review before final. Priority (higher
first), then submission time, orders jobs **within** a lane. It never lifts a
job into an earlier lane.

When a job in an earlier lane arrives, a running review or final with frames
still to render after the current one is **swapped out at its next frame
boundary**. The runner waits for the frame in progress to land, however long
that takes, then stops the job cleanly. It runs the earlier job, then
relaunches the swapped-out one. Preemption never stops a frame mid-render. A
job is only swapped out once it has rendered for `preempt_min_run_seconds`
(20 minutes) since its last launch, because every swap costs it a scene
rebuild. So a light job can wait up to 20 minutes, and should batch a whole
review round into one job rather than one job per part or view. A job swapped
out before its 20 minutes were up goes back in ahead of the next light job.

A job without a `frames` check, or one on its last frame, is not swapped:
it finishes first, then the earlier lane goes next. That includes a long
single-frame still. A two-hour single frame therefore holds its machine for
two hours. Submit quick looks as `any` so the other machine can take them, or
ask the render coordinator to hold the long job, which stops it mid-frame and
throws the frame away.

Closing the gate, or a hold, swaps a job out the same way with nothing swapped
in. These wait at most `swap_wait_max_seconds` (7 minutes) for the frame in
progress, then stop it mid-frame.

So a multi-frame render **must skip frames that already exist**, as Blender
does with `use_overwrite = False` / `use_placeholder = True`. The runner
deletes 0-byte placeholders before each launch.

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
  `review/<asset>/`, with `{machine}` in its name (see `owner-review.md`); write its
  `NOTES.md` yourself once the job is done.
- `frames`: completion is decided by counting non-empty files matching `glob`
  in `dir`. The exit status does not decide it. Without `frames`, exit status
  0 is success.
- `keep_checkout: true` keeps the worktree (and work scratch) after the job;
  `low_priority: false` runs the job at normal priority. Without it the runner
  keeps the `rq-<id>` checkout and `work/<id>` scratch after the job, and an
  idle sweep (every ten minutes, never while a job runs) removes them later:
  12 h after an ok result (`retain_ok_hours`; a copy into the Drive folder only
  means Drive accepted it locally, not that it uploaded), 48 h after a failed or
  cancelled one (`retain_failed_hours`). A job whose output copy failed is
  never swept: the runner logs it and leaves the files for a person to copy.
  Active, queued and held jobs are never touched. Don't name a session worktree
  `rq-*`: the sweep treats that prefix as the queue's own.

## Splitting a shot into parts

A long render goes in as small parts that either machine takes, not as one half
pinned to each machine. A free machine takes the next part, so the faster or
less loaded one does more and nobody rebalances by hand. A failure costs one part,
and any span can be redone later, down to a single frame:

```bash
python tools/render_queue.py split template.json --frames 2112:2448 --part 48 \
    --assemble assemble.json --commit HEAD [--dry-run]
```

- The template is an ordinary job whose strings use `{part_first}`,
  `{part_last}`, `{part_count}` (frame numbers) and `{part_start_s}`,
  `{part_end_s}` (seconds at `--fps`, default 24; the end is exclusive), for
  example `"--span", "k4:{part_start_s}:{part_end_s}:24"`. `split` fills them
  in for each part, sets `frames.count` to the part's length, and defaults
  `machine` to `any`.
- `--frames FIRST:END` takes frame numbers, END exclusive. A part's id is the
  template id plus `-f<first frame>-<length>`, so the same cut always gets the
  same name.
- `--assemble` is submitted last, with `after` listing every part. Assemblies
  stay pinned to Windows (Mac Drive stalls on downloads).
- Every part must render the same picture whatever order the parts run in.
  That is the scene's job, not the queue's: anything a frame depends on is
  computed from the shot, never from the part's first frame or from frames
  rendered earlier in the same process.
- About a minute of each part goes on checkout, inputs and building the
  scene, so 48 frames (2 s at 24 fps) costs a few percent. Go smaller to redo a
  short span.

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

**Mac.** The runner lives in `~/render-queue/`. The LaunchAgent
`com.starsectormarines.renderqueue` starts it at login and restarts it if it
dies. To update it, copy the script over and run `install`:

```bash
C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local dillon@192.168.1.97 "cd ~/render-queue && /usr/bin/python3 render_queue.py install"
```

The agent needs Full Disk Access to read the Drive folder, which the owner has
granted. The grant is on the interpreter the plist runs, the Command Line
Tools `Python.app`
(`/Library/Developer/CommandLineTools/Library/Frameworks/Python3.framework/Versions/3.9/Resources/Python.app`).
A grant on the `/usr/bin/python3` shim does nothing. A Command Line Tools
update can replace that `Python.app` and drop the grant. The runner log then
fills with `PermissionError: Operation not permitted` and the Mac heartbeat in
`status` goes stale. When that happens, ask the owner to grant it again, or
use `install --mode nohup` from ssh as a stopgap; that copy does not survive
a reboot.

`config.json` belongs to the render coordinator. Besides gates, holds and
priority overrides it holds:
- `preempt` and `preempt_min_run_seconds`
- `swap_at_frame_boundary` and `swap_wait_max_seconds`
- `mac_swap`: `stop` (the default, which frees memory) or `freeze` (SIGSTOP)
- `low_priority`
- `light_max_seconds`
- `any_grace_seconds`
- `block_on_foreign_blender`: a runner starts nothing while a background Blender it did not start is rendering
- `poll_seconds` and `claim_wait_seconds`
- a reserved `schedule` slot for a later night window

The gate stays manual.
