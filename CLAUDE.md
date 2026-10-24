# Project: Starsector Marines

A Starsector mod (game version 0.98a-RC8). Source-of-truth game install is at
`C:\Program Files (x86)\Fractal Softworks\Starsector` — read-only reference, never edit.

For project vision, current focus, and immediate next-up, see
[`roadmap/`](roadmap/). Read `roadmap/README.md` first. Within a feature,
start with its canonical noun doc for the high-level model and
`design/stories.md` for the open-work board.

## Agent model selection

Subagents and sub-sessions run on Claude Code's own models: **Opus**, **Sonnet**
and **Sol 6.1**. Pick one per job with the `model` argument of the Agent or
start-session tool, set explicitly rather than inherited from the orchestrating
chat:

- **Opus** for design-heavy work: modelling and art direction, architecture,
  tricky debugging, review that needs judgement.
- **Sonnet** for mechanical or read-only work: reconnaissance, searches, sweeps,
  bulk edits, running a known command and reporting.
- **Sol 6.1** for reconnaissance and implementation when the tool offers it; if
  it does not, use Opus or Sonnet by the rules above and carry on.

The user may name a different model, and that wins.

**Codex CLI is for image generation only**, through the `codex-imagegen` skill
(`tools/codex_imagegen.py`). Never use `codex exec` or any other Codex call for
reconnaissance, coding, review, planning or anything else a Claude agent can do:
spawn a Claude subagent or session instead.

## Session worktrees (default workflow)

Keep the main workspace checked out on `main` and free of session edits so it
remains available as the integration point for concurrent sessions. For every
task that may change repository files, use this workflow unless the user
explicitly asks for a different one. Read-only investigation does not require a
worktree.

1. At the start of the task, inspect `git status` and `git worktree list`. If the
   session is already in a linked worktree under `.claude/worktrees/`, use it;
   do not create a nested worktree.
2. Otherwise, from the main workspace, create a uniquely named branch and
   linked worktree based on the current local `main`:

   ```powershell
   git worktree add -b session/<unique-name> .claude/worktrees/<unique-name> main
   ```

3. Change the session's working directory to that worktree. Perform all edits,
   builds, tests, staging, and commits there. Never make task changes directly
   in the main workspace.
4. Stage only paths owned by the session and commit all intended changes on the
   session branch. Do not use `git stash`, and do not modify, remove, or reuse
   another session's branch or worktree.
5. Before integration, fetch GitHub and merge **both** the latest local
   `main` and `origin/main` into the session branch inside the worktree,
   resolving conflicts there. Re-run relevant verification.

   ```powershell
   git fetch origin
   git merge main
   git merge origin/main
   ```

6. Integrate with the script, which does everything below and stops at the
   first failure: `powershell -File tools/integrate.ps1 session/<unique-name>`
   (`tools/integrate.sh session/<unique-name>` on the Mac). It checks that the
   main workspace is on `main` and clean, fetches, fast-forwards `main` to
   `origin/main`, fast-forward-merges the session branch, pushes `origin`, and
   only then pushes `hub` (skipped with a message when the Mac is unreachable).
   Use it rather than typing the commands: three sessions (2026-10-20 and
   2026-10-21) pushed other sessions' commits early after a failed
   `--ff-only`, because the push was chained unconditionally or a pipe hid the
   exit code.

   The manual fallback: return to the main workspace, verify that it is still
   on `main` and clean, then integrate with a fast-forward-only merge, publish
   `main` to GitHub with an ordinary (fast-forward) push, and then mirror it to
   the Mac. Never chain the push after the merge unconditionally, and never
   pipe these commands; check each exit code.

   ```powershell
   git merge --ff-only session/<unique-name>
   git push origin main
   git push hub main
   ```

   If `main` advanced and the fast-forward fails, or the push is rejected
   because `origin/main` moved, go back to the session worktree, fetch and
   merge `main` and `origin/main` again, verify, fast-forward local `main` and
   push again. The `hub` push only follows a successful `origin` push; when
   the Mac is unreachable, skip it. **Never force-push**, and never push session branches. If the
   main workspace has uncommitted changes, do not absorb, discard, or
   overwrite them; leave the worktree intact and report the integration
   blocker.
7. Only after confirming that the session commit is reachable from `main`,
   remove the linked worktree and delete its merged branch from the main
   workspace:

   ```powershell
   git worktree remove .claude/worktrees/<unique-name>
   git branch -d session/<unique-name>
   ```

### Standing authorization to integrate

For any task in which the user has authorized repository changes, the repository
owner gives standing authorization to complete the entire local Git workflow:
stage the session's owned paths, commit the verified change, merge the latest
local `main` and `origin/main` into the session branch, fast-forward local
`main`, push `main` to `origin` and then `hub` (fast-forward only), and clean up the merged
session worktree and branch. **Do not pause to ask for a separate
"merge it?" confirmation. Always merge a green, self-contained change into
local `main` when the workflow above permits it.**

This standing authorization does not turn a read-only diagnosis, explanation,
review, or status request into permission to edit files. It also does not
authorize force-pushing, pushing any branch other than `main`, deploying the
mod, publishing artifacts, or discarding unrelated work. Those actions still require scope from the user's
request or another explicit project instruction.

Integrate at every coherent commit chain, not once at the end of a session.
A session that has reached a green, self-contained state should merge to `main`
before starting the next chunk, then keep working in the same worktree. Banking
several chains and merging them together only widens the window in which a
sibling session's merge turns into a conflict.

### More than one machine

**GitHub (`origin`) is the source of truth** (owner, 2026-10-18). Every
integration lands on `origin/main` first; a change that is not there is not
integrated. The project is worked on from this Windows workstation and a
MacBook Pro, each with its own clone and its own local `main`.

The Mac also holds a bare mirror, `/Users/dillon/git/starsectormarines.git`,
the remote `hub` in every clone. It is an accelerator, never an authority: a
fast LAN copy for fetching and for setting up a clone or session quickly. It
only ever receives `main` after `origin` has accepted it, so it equals
`origin/main` or lags it. Never integrate against `hub/main`, never push to
`hub` what `origin` has not taken, and when the two disagree `origin` wins.

- Fetching: `git fetch hub` first is optional and makes the following
  `git fetch origin` nearly free on a big catch-up; `origin/main` is what you
  merge.
- On Windows, `hub` is `ssh://dillon@192.168.1.97/Users/dillon/git/starsectormarines.git`
  and the clone sets `core.sshCommand` to
  `C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local`. The
  `dillmac.local` name resolves only intermittently from this machine, so the
  remote names the Mac's address and the alias keeps the host-key check on the
  Mac's own key. If the address changes, update the URL; never drop the alias.
- On the Mac, `hub` is the local path `/Users/dillon/git/starsectormarines.git`
  and `origin` is GitHub over SSH.
- At the start of a session, `git fetch origin` and fast-forward local `main`
  to `origin/main` from the main workspace when it is behind, before cutting
  a worktree.
- History was rewritten on 2026-10-18 to drop rendered output (every commit id
  changed; trees did not). A branch or worktree cut before that is on the old
  history: never merge it into `main`, which would bring every purged render
  back. To move one onto `main` once it holds no commits of its own, first
  list the files the reset would delete
  (`git diff --name-only --diff-filter=D HEAD main | git check-ignore --stdin > list`),
  then `git reset --hard main`, then
  `git restore --source=<old HEAD> --worktree --pathspec-from-file=list`:
  a reset deletes renders that were tracked at the old commit even though
  they are ignored now. Never `git gc` or prune while old worktrees exist;
  the old objects are their only recovery source.

### Never commit rendered output

A render is an output of a script, not a source: regenerate it, never commit
it (owner, 2026-10-18). Review renders, contact and review sheets, overlays,
frame sequences and graded textures are written under ignored paths
(`build/`, or the patterns in `art-source/company-prologue/.gitignore`) and
shown to the owner from disk. What stays tracked is what cannot be rebuilt
from the repository: scripts, data, Codex/ImageGen generations with their
prompts and the guides they were generated from, finish references, and
hand-made or story art. An ignored path refuses `git add`; never force it. Untracking a file (`git rm --cached`) deletes it from every
other checkout that merges the commit, the main workspace included: restore
it there with `git restore --source=<commit before the untrack> --worktree --
<paths>`, since the file is ignored from then on and nothing is staged.

The main workspace is for brief integration and worktree administration only.
Do not run builds or leave generated task files there.

### Large files: Google Drive

Rendered output and other large regenerable files that should reach the other
machine (or the owner's phone) go to the shared Google Drive folder, never into
git: `G:\My Drive\starsector` on Windows and
`~/Library/CloudStorage/GoogleDrive-<account>/My Drive/starsector` on
the Mac. It syncs in about half a minute. It is a transfer and storage area,
not version control and not how the owner is shown work (see below), and a build copies what it needs local before reading it.

#### Review sets for the owner

Every set of images or video put in front of the owner for a decision goes
under `starsector/review/`, laid out **by asset, then by run**:

```
review/<asset>/<YYYY-MM-DD>_<what>_<machine>/
    NOTES.md
    01-<view>.png  02-<view>.png  ...
    <subfolder>/   (only when a run has natural groups: states, close-ups)
```

- `<asset>` is the package's slug without its suffix: `second-wind`, `hound`,
  `mudskipper`, `legion`, `hermes`, `shepherd`, `fighters`, `cinematic-<shot>`.
  One folder per thing the owner reviews, whichever session or machine made it.
- `<what>` names the pass or question in a few hyphenated words
  (`pass2-detail`, `pass6-texture`, `damage-round3`, `c2-composites`).
  `<machine>` is `windows` or `mac`. The date sorts runs oldest first, so the
  newest run is the last folder.
- **One run, one writer.** A session creates a new run folder and writes only
  into it; it never adds to, renames or deletes another run's folder, and a
  re-render after feedback is a new run, not an overwrite. That is what keeps
  sessions on two machines from producing Drive conflict copies.
- `NOTES.md` is short: the commit the renders were built from, what changed
  since the last run, what the owner should look at and decide, and any
  measured numbers (IoU, scale, coverage). Image names start with a two-digit
  order so the folder reads in the order the notes describe.
- Images obey the preview rule (1920x1080, 100% scale). Copy finished images
  only, never frame sequences or scratch.
- **Owner-facing images come from the look preset, never from a verdict
  scene's arena frames** (2026-10-23). A behaviour scene's loops are
  drawn on a bare arena with faction dots, labels and a caption, and without
  smoke, contrails or shadows: evidence for a verdict, not a picture of how
  anything looks. Give the scene suite a `lookShots()` (`LookReview`: the
  subject on real generated ground, effects and shadows on, no annotations,
  play zoom and close) and show the owner `build/snapshots/<scene>/look/`;
  `-Dsnapshot.lookOnly=true` renders only those. See `snapshot-suites.md`.
- **Show the owner the images; never send them to Drive to look** (owner,
  2026-10-22). The run folder is storage and transfer, not how a set is
  delivered. When a set is ready, put the images themselves in front of the
  owner: send them into the chat (`SendUserFile`, `display: "render"`) or
  publish them on a preview or picks artifact page. A message that says
  "review in G drive" or gives only a folder path does not count as
  delivering a set. A session started by another session gives its
  coordinating session the image paths, and the coordinator shows them.
  The folder path may follow as a footnote, never in place of the pictures.
  **A finished-work preview that asks nothing (a GIF of a feature, a set of
  stills) goes on the parent's picks page too** (owner, 2026-10-23), in its
  Previews section: a `previews.json` of `[{id, area, date, title, note, lead?,
  shots:[{file,label}]}]`, newest first, passed to `build.py --previews`, with
  the media under `media/<preview id>/`. Sending it into the chat as well is
  fine; the page is where the owner looks.
  Look and design choices go to the owner the same way: as labelled pictures
  (an `options.json` of `{id, question, options:[{key,label,file,note}]}`
  for the picks page), not as a description in words.

#### Questions for the owner: the picks page

**Every decision the owner has to make goes on a picks page as picture
cards, never only in chat** (owner, 2026-10-22). A question asked in a chat
message is lost the moment the owner switches to another session; the page is
the one place they answer from.

**One picks page per top-level parent session** (owner, 2026-10-23). The
owner works session by session: each top-level session in the sidebar is a
parent with its own bundle of child sessions, and each parent owns one picks
page for its bundle's questions. Never post to another parent's page. A
parent's page is an Artifact built from `tools/picks/template.html` with
`python tools/picks/build.py --title "<parent> picks" questions.json out.html`,
published with the `db` capability; each answer is a `db` document
`picks/<question id>` holding `{choice, note, at}`. A parent that has no page
yet creates one when it has its first question and adds a row to
`tools/picks/pages.md` (parent session title, page URL) in its next commit.

- **A question is an `options.json`**: `[{id, question, options:[{key, label,
  file, note}]}]`, with one 1920x1080 image per option rendered on the real
  scene (real ground, real buildings, play zoom), a short `note` saying what
  each option does in play, and `(recommended)` in the label of the one you
  would pick. Yes/no and "how big" questions get pictures too.
- **Sub-sessions raise it to their parent; the parent owns the relay**
  (owner, 2026-10-22). A session started by another session never posts to the
  page or asks the owner itself: it sends its parent the `options.json` path,
  the image paths and what it is building on meanwhile. The parent passes it up
  the same way until it reaches the top-level parent session, which
  publishes the question on its own page, reads the answer from the `db` and sends it back down
  to the session that asked. A parent that is itself the top relays straight
  away.
- **The top-level parent posts.** Read its own page (`Artifact` `read`), add
  the question to the page's `QUESTIONS` array and its images under
  `media/<question id>/`, republish to the same URL (merging, never
  overwriting, if the publish reports a newer version), and read the answer
  from `picks/<question id>`. Use JPEG for option images: a page holds at most
  256 MiB across its versions.
- **Don't wait on the answer.** Build on the recommended option meanwhile and
  switch if the owner picks otherwise. Tell the owner in one line that a
  question is up, and nothing more.
- Drive keeps two folders that two machines created under the same name and
  shows the second as `name (1)`. It happened once, to `review` itself. Create
  only your own run folder (the asset folder only if it is missing), and if a
  `(1)` twin appears, move its contents into the original and remove the twin.
- **Cleanup is automatic.** After posting a run, prune its asset:
  `python3 tools/review_prune.py --asset <asset> --apply` (`python` on Windows).
  Each asset keeps its three newest runs plus any the owner marked, either
  with a folder name ending `_keep` or with a file named `KEEP` inside.
  Deletes go to Drive's trash for 30 days. Without `--apply` it only reports.

## Build & deploy

- **Never run `gradlew --stop`, and never make it a retry step.** It stops every
  daemon on the machine, not just yours. Concurrent sessions are the norm here,
  so a single `--stop` aborts whatever they are running: their `:test` runs fail
  with `Gradle build daemon has been stopped: stop command received`, and a
  `runStarsector` session loses the game itself, because the game is a child of
  the daemon and goes down with the build. That looks exactly like a silent game
  crash from the inside — exit status 0, no exception, no `hs_err`, no shutdown
  hook, no `run-summary.txt` — and one such "crash" cost a long investigation
  before the daemon log gave it away at the matching second. As a retry step it
  is self-feeding: the stop fails other sessions, whose retries stop more
  daemons. A build that seems stuck is nearly always another session holding a
  lock; wait, or run the one task you need. To confirm a stop after the fact,
  grep `~/.gradle/daemon/<version>/*.log` for `stop() called on daemon` and
  compare the timestamp. Routine `other compatible daemons were started ... idle
  for 0 minutes` entries are ordinary culling of idle daemons and are harmless.

- Shell `JAVA_HOME`: `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr`.
  Set this explicitly before invoking Gradle from PowerShell; do not guess a
  Java install or substitute Starsector's bundled runtime:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr'
  ```

- Toolchain: Eclipse Adoptium JDK 25 (registered via Gradle's auto-detected toolchain).
- Bytecode target: Java 17 (`--release 17`). The game ships Zulu 17.0.10 + `--enable-preview`,
  so do NOT use language features newer than Java 17, and do NOT rely on preview features
  at compile time.
- `gradlew.bat build` → `mod/jars/StarsectorMarines.jar` (directly into the mod folder; no
  intermediate copy step).
- Tests read data off disk rather than off the classpath, so a checked-in file a
  test opens at runtime must be declared with `inputs.dir` in `build.gradle`'s
  `test {}` block, or `:test` reports UP-TO-DATE with the change unverified.
  Never declare a build output (`mod/jars`, `mod/sounds`) that way — a suite that
  re-runs on every build is a suite people start skipping.
- `gradlew.bat commanderEvidence -Pmission=conquest` → runs the selected
  mission's documented construction-fixture matrix in a forced-serial,
  zero-input simulation and writes canonical traces plus `summary.json` /
  `summary.md` under `build/reports/commander/<mission>/`. Supported mission
  arguments are currently `conquest`, `conquest-trial`, `sabotage`, `assault`, `raid`, `extraction`, `relief`, `counterattack`, `junction`, `last-lift`, `relay-nine`, `thunder-run`, `supply-route`, `corvus`, `colony-breach`, `cordon-line`, `cordon-road`, `heart`, and the story missions `yard-owners`, `water-under-seal`, `limited-warrant`, `district-escort`, `terms-of-protection`, `small-war`, `salvage-rights`, `cold-start` and `working-passage`; later mission harnesses
  extend that argument instead of creating another Gradle task. The run is
  opt-in and excluded from ordinary `test` / `build`.
  **Each fixture is replayed once.** It used to be replayed twice, with the two
  traces asserted byte-identical, which on Conquest meant four ~105s replays
  and 422s of wall clock — and the assert is a determinism check on the
  simulation rather than evidence about the battle, so it does not need to ride
  along on every balance run. `simDeterminism` below owns it. `-Prepeat=2`
  restores the double replay here when a particular balance run wants it.
  Replays run **side by side by default**, on half the machine's processors —
  `-Pparallelism=N` sets that explicitly and `-Pparallelism=1` is the serial
  control. It was serial until `simDeterminism` found two process-globals —
  `LosCache` and the map generator's own preview fields — and both are fixed;
  two replicas now produce byte-identical traces.
  **Conquest is the slow one, and slow is not stuck.** At 560x336 the matrix
  takes about 17 minutes of wall clock with the fixtures side by side (one
  replay each; the other four missions finish in under half a minute), because
  a 400-seat fixture on that map runs near 30 ticks/s before contact and slower
  after it. A run with no output for twenty minutes once looked like a frozen
  map and was killed; it was honest work. Before killing one, read the staging
  trace under `build/reports/commander/.conquest-staging-*/traces/` — its
  `compound-state` events say whether the battle is moving — or ask the cheap
  question first: `-PmaxTicks=3000` plays both fixtures in about 100s.
  Per-mission matrices, fixture fields and report contents (Relief, Counterattack,
  Junction, Relay Nine, supply-route, power-suit-campaign, Corvus, Thunder Run,
  conquest-trial and the Conquest fort control) live in
  `roadmap/battle-render/design/commander-evidence-missions.md` — read it when you
  need that mission's details.
  Use
  `-PmaxTicks=9000` or
  `-Pfixture=C:\path\to\fixture.json` for explicitly ad-hoc evidence. `-PevidenceDir=<path>` writes the report
  somewhere other than `build/reports/commander/<mission>/`, so a run and its
  control run do not overwrite each other. Add
  `-PsnapshotEveryTicks=300` to render neutral-observer PNG frames from the
  first replay and assemble `visuals/<fixture>/review.gif`; the frames add
  cyan marine, red defender, and yellow civilian markers for whole-map review.
  They are also **annotated**: every compound is boxed and labelled with what
  it is and who holds it (the command post reads `KEEP`, in its own colour),
  the ground the marines came ashore on is boxed and reads `LZ`, and an arrow
  runs from the beachhead to the keep. Without them a whole-map Conquest frame
  is a picture of a city in which nothing says which grey rectangle is the
  headquarters. `-PsnapshotAnnotations=false` renders the bare scene.
  `-PsnapshotFocus=x,y,cellsAcross` frames a close view on that cell, with
  shots and impacts, instead of the whole map. `-PsnapshotFromTick=T` plays
  the ticks before `T` undrawn, so a GIF opens on the moment it is about. Optional
  `-PgifFrameDelayMillis=125`, `-PsnapshotWidth=960`, and
  `-PsnapshotHeight=640` arguments control review playback and output size.
- **Memory budget: measure before raising the heap.** Every Test task,
  `battleProbe`, `sceneEvidence` and `sceneDeterminism` run under
  `-PtestHeap` (default 6g); `createSnapshots` keeps the JVM default unless
  given it. A battle's live set is small: a framed The Burn (heart, seed 202,
  1920x1080 every 300 ticks, to its end at tick 9,607) peaks near 0.3 GB, an
  unframed 9,000-tick Conquest sits flat near 0.22 GB, and both finish under
  `-PtestHeap=2g`. Its 2g OOM (2026-10-22) was not frames held for the GIF —
  `AnimatedGifWriter` streams, and each frame is written and dropped — but the
  headless sprite tokens closing over every decoded sheet (1.3 GB before a
  battle began) plus a tinted-cut cache that filled to its 256 MB cap on every
  burn. So when a run dies of OOM, find the retainer rather than the flag:
  `-PtestHeap=2g "-PtestJvmArgs=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<dir>"`
  reproduces it cheaply and leaves a dump, and
  `-XX:StartFlightRecording=filename=<file>.jfr,settings=profile` in the same
  property gives the heap-after-GC curve (`jfr print --events jdk.GCHeapSummary`).
  A curve that rises with ticks and never falls is a leak to fix; a
  render-side cache must be bounded by what the frame draws, not by a size cap
  it reaches every run. Raise the heap only for a live set that is really that
  big, and say what it is. Never run two `commanderEvidence` at once in one
  worktree: they share `build/test-results/commanderEvidence` and the second
  fails on the first's files.
- `gradlew.bat simDeterminism` → replays the Conquest matrix twice for a bounded
  tick budget (`-PmaxTicks`, default 3000) and fails on the first byte that
  differs, writing under `build/reports/sim-determinism/`. It exists because a
  balance run was paying for a determinism check on every fixture at full
  length: the question "does the same fixture play the same way twice" needs one
  fixture and a few thousand ticks, not eighteen thousand of two. Opt-in and
  excluded from `test` / `check`.
  The two replicas run **side by side by default** (half the machine's
  processors; `-Pparallelism=1` is the serial control), which asks a second
  question the serial form cannot: whether two simulations in one JVM can see
  each other. **They could, and this found it on its first run.** The sim's
  per-thread scratch is all `ThreadLocal`, but `LosCache` was not per
  simulation — its enable flag was one static volatile that every sim raised at
  its own tick top and lowered at its own tick end, and `clearAll` swept every
  cache in the process rather than the caller's own. So one battle emptied
  another's line-of-sight cache mid-tick and switched its caching off, and two
  replicas of `reinforced-south` that agreed for three hundred ticks recorded
  the same compound-presence change one tick apart. A cache now belongs to the
  `NavigationGrid` whose topology invalidates it, so a sweep reaches only that
  grid's own.
  **The second thing it found was worse, and only concurrency could find it.**
  `BspCityGenerator` is one instance shared by every battle in the process, and
  `assembleResult` stored the finished tactical map, road graph and biome map on
  `this` before handing them to the `MapResult` — reading them back off the
  instance rather than out of the context it had just generated. So a battle
  whose generation overlapped another's was built on **the other battle's
  map**: a `reinforced-south` replica came out with `full-strength-west`'s
  compounds and its garrison allocated against them. It is composed from locals
  now; the `last*` fields remain as the single-threaded preview seam the render
  tests read. Nothing serial ever saw it, and nothing serial ever will.
- `gradlew.bat profileConquestTail` → opt-in 30 Hz-paced, continuous Conquest
  tail pass through the production async-route and parallel-unit scheduler.
  It runs the checked-in full-strength fixture for 1200 ticks by default,
  excludes the first 300 from timing, and writes
  `build/reports/performance/conquest-tail/summary.json`. The switch and report
  catalogue is in `roadmap/battle-render/design/conquest-tail-profile.md` — read it
  when you need that task's details.
- `gradlew.bat profileConvoyRoute` → isolated full-size Conquest route-proof
  evidence on both traversal axes, independent of whether the timed battle
  selects convoy as its reinforcement means. It reports first-dispatch snapshot
  time, total and worst proof-step time, search attempts and node expansions,
  and how many clearance and terrain-cost cells were actually derived out of
  the 560x336 map under `build/reports/performance/convoy-route/summary.json`.
  Use `-Dbattle.convoyRoute.outputDir=<absolute path>` to redirect the report.
  Its timings are diagnostic, not a cross-host gate.
- `gradlew.bat profileSwarmFlow` → swarm strategic-movement cost on generated
  cities at 280x168, 400x240 and 560x336 with 64 to 1000 runners chasing three
  walking Marine columns, flow field on and off in the same run (serial
  scheduler, one sim at a time: per-tick median, spread, p99, max and ticks over
  10 ms), then two replicas of each lever side by side for the determinism
  check; fails if they differ. `build/reports/performance/swarm-flow/summary.md`.
  `-Dbattle.swarmFlow.sizes=400x240`, `.counts=150,300`, `.levers=on`, `.spawnDistance=20` (a melee window),
  `.measureTicks`, `.warmupTicks`, `.outputDir` narrow it. Opt-in, excluded
  from `test`. Readings and the decision they support: `swarm-movement-cost.md`.
- `gradlew.bat garrisonEvidence` → stands up every story mission at tick zero
  (the debug mission catalog plus the three Agreus maps) and inventories what
  each map offers a squad to hold: per tactical node, windows / entrances /
  distinct stand cells at building and compound scope and fighting-position
  runs, plus the windows and runs no node covers. Writes
  `build/reports/garrison/summary.md`. Map evidence, not battle evidence; opt-in
  and excluded from `test`. See `garrisonables.md`.
- `gradlew.bat generatedSpaceEvidence` → the JUnit assertions that have to
  generate a map, stand up a mission or man a deck to ask their question:
  scans over canonical Conquest maps, city landing sites, forward-bunker
  windows, Conquest setup and arrival plans, launch fixtures, filler batches
  over representative cities, deck-scene runs. Tag a method or class
  `@Tag("generated-space-evidence")` to put it here. Each took over a second,
  which is what moved it out of `test`; it still asserts, so run it when
  touching world generation, setup or the deck sim. Opt-in and excluded from
  `test`.
- `gradlew.bat loadoutCardSheet` → the design-pass stat sheet for every loadout card,
  written to `build/reports/loadout-cards/cards.md` and `cards.csv`. Each card lists its access
  tier and unlock gate, the twelve billets seat by seat (weapon, grade, special, or armor pattern
  with its capacity / rating / speed / system), strength as `LoadoutEffectiveness` measures it
  (effective DPS and FIRE rating; absorbed damage and GUARD rating) with matchup columns
  (weapons vs bare / line / suit armor, suits vs pen 1 / 7 / 14 fire), and reach, specials,
  mobility and systems kept as separate columns, plus which faction channels sell it, followed
  by the armor-pattern and weapon-grade reference tables. Read from the shipped data, so it
  cannot drift; opt-in and excluded from `test`. Ratings leave out cover, morale and orders.
- `gradlew.bat loadoutMatchup` → fights loadouts against each other headlessly and writes
  `matchups.md`, `matchups.csv` and per-fight `runs.csv` under `build/reports/loadout-matchups/`.
  Twelve regular marines a side, kit exactly as the cards issue it (`LoadoutCardIssue.seats`:
  the weapon card's billet plus the sheet's pattern for that billet), in three starter
  scenarios on one skeleton (attackers attack-move in from the west, defenders hold the east):
  `open-field`, `trench` (a twelve-cell trench square across the approach, manned with the
  commander's `defendRun` order so claims stay on the run), `trench-vulcans` (the same line with a
  Vulcan emplacement past each end; the defenders' side is not beaten until its turrets are
  destroyed too, and `runs.csv` reports turrets left and turret rounds) and `building` (the production
  `MARKET_HALL` stamp with its windows, internal walls and furniture, held by a garrison set up as a
  generated one is, attackers given `secureCompound`). The default ladder fights every weapon card (on
  starter armor) and every sheet (with starter weapons) against the starter issue, once
  attacking and once defending, at `-Pseeds=3` seeds; starter-vs-starter is the control row
  every other row is read against, because the ground favours one side. `-Pattacker=` /
  `-Pdefender=` (`weapons:x+armor:y`) replace the ladder with an explicit matchup;
  several sides separated by `;` fight every pairing. Either half may be an uncarded
  experiment, for trying an issue before authoring it: `kit:field-rifle@surplus,rocket-launcher*2,frag-grenade*6`
  (every seat the primary at that grade, the listed specials on the first seats in order) and
  `mix:cordon-shell*10,packframe*2` (armor patterns adding up to twelve); the `weapon.`,
  `special.` and `armor.` prefixes may be left off. `-PfireMission=true` gives the attackers'
  order a fire mission on the ground the defenders start on, as a commander does for a squad
  carrying crew-served tubes; without one a marching squad never halts to set a field mortar
  down, so a tube on the attack is idle against a trench or building. `runs.csv` reports each
  side's specials used and its mortars' set-ups, rounds, damage and kills (a tube is its own
  entity, so its fire is not in the squad's figures) and the hostile rounds its point defence engaged
  and stopped. `-Pscenarios=open-field,trench` selects ground, `-Pparallelism=N` sets fights run side by
  side (default half the processors; 1 is the serial control). The sim seed changes every hit
  and morale roll and nothing else. A fight ends when a side is wiped out, when both stand
  with no damage for 1,200 ticks (`STALEMATE`) or at the tick limit; neither is a draw, so
  read survivors. Each run replays the control twice side by side first and reports a
  determinism failure in the header. Any `-Dbattle.*` switch is forwarded, so a behaviour
  tweak shows as the rows that move between a run and its control run. Controlled
  construction with a detached kit, not a campaign roster, and not balance acceptance.
  `-Pgifs=true` records each fight's first `-PgifSeeds=1` seeds as a 1920x1080 GIF under
  `gifs/<scenario>/` (linked from the Replay column of `matchups.md` and `runs.csv`); it is on
  by default for up to four explicit `-Pattacker`/`-Pdefender` pairings and off otherwise, where it
  would write a gigabyte. A recording only reads the sim: sparse frames until the first damage,
  dense after it, shots and impacts drawn, a camera that follows the attackers' main body until
  the first damage and both sides' after it (a routed straggler is left out of the framing, so a
  standoff fight frames both places), capped at 360 frames. A scenario's walls have
  no art, so a scenario overrides `annotations()` to box what it built. Rendering takes turns
  across the side-by-side fights and costs about 20-30s a recording.
  A new scenario implements `MatchupScenario` and joins `MatchupScenarios.all()`.
- `gradlew.bat armorMatchup` → machines against machines, the sibling of `loadoutMatchup` with its conventions
  (`-Pattacker`/`-Pdefender`, `;` for every pairing, `-Pseeds` (default 2), `-Pscenarios`, `-Pparallelism`,
  `-Pgifs`/`-PgifSeeds`, `-Dbattle.*` forwarded, the control replayed twice side by side for the determinism
  header). Writes `armor-matchups.md`, `armor-matchups.csv` and `runs.csv` under `build/reports/armor-matchups/`.
  A side is `unit(+unit)*`; a unit is `[Nx]` then a mech (`hound`, `bulwark:refit`,
  `bulwark:arms=linear-cannon,rs=shoulder-laser,engine=uprated,vents=auxiliary,role=assault`: pattern ids with
  or without their `component.mech-`/`internal.` prefix, `arms` fits every arm mount and `la`/`ra` one Bulwark arm, `none` empties a mount, `refit` fills the best buildable
  internals around the fitted weapons, an over-budget fit runs and is labelled so) or a `VehicleType` in kebab case
  (`breakthrough-tank`; `technical`, `scout`, `dozer`). Scenarios: `open-ground`; `hull-down` (berms hide the
  defenders' legs and hulls, ruins in the middle ground); `street` (between solid blocks, defenders behind the last
  two corners). Attackers are moved as a player would move them, to a cell at about 70% of their longest reach that
  sees the nearest enemy (`-Pstandoff=0.7`), and closer if they stand in range without firing; inside range it is
  production doctrine and turrets. A mech is steered only while out of reach or out of sight, and its lance
  carries no mission order of its own, so doctrine (and its duel range against armor) decides where it fights;
  `-PmechOrder=attack-move` restores the old attack-move onto the defenders' ground and the old steering, which
  together walked every attacking mech into contact. Both sides are set down facing each other;
  `-PdefenderFacing=90|180` turns the defending hulls side-on or rear-on (a standing mech pivots onto its
  target at once, so mechs are not turned), the question facing-dependent armor asks
  (`-Dbattle.vehicle.armorArcs=false` is its control). On open ground `-PattackerSpread=<degrees>` fans several
  attackers around the defender (180 puts two on opposite sides) and `-PattackerRadius=<cells>` caps how far out,
  so a narrow fan at the same radius is the head-on control for a two-angle fight. The Range column is the median distance between the nearest
  opposing machines from the first hit. The default ladder (`-Pladder=control,mech,mixed,vehicle`): stock Hound vs
  stock Hound (the control), every chassis at stock and refit against every other both ways, each stock chassis
  against each armed vehicle line both ways, every armed line against every other (the unarmed scout car only when
  named); about 800 fights in well under a minute. Rows report wins, TTK from first hit, machines and integrity
  left, hit rate, mounts lost, legs crippled, flux-limited seconds and damage by weapon (the side's dealt damage
  apportioned by hits x nominal damage); `runs.csv` adds each survivor's sections.
  One machine a side, no cost or lift parity: read rows against each other, not as balance acceptance.
- `gradlew.bat bloomEvidence` → the Rav Bloom on the campaign layer: runs
  `RavBloomSystem` with the real event lifecycle over `-Pyears=20` cycles of a
  synthetic vanilla-shaped sector at `-Pseeds=8` save seeds, for every severity
  and frequency preset under three player policies (ignores, answers half,
  answers all), and writes blooms a year, outcomes, spread and size lost to
  `build/reports/blooms/summary.md` / `summary.json` (`-PevidenceDir`). About
  fifteen seconds. Readings, not acceptance; opt-in and excluded from `test`.
- `gradlew.bat storyMapPools` → the Rav Bloom story maps (Cordon Line and
  Road, The Burn, Broad Front) play only from their pools of vetted seeds,
  three per world biome, `mod/data/world/pools/<map>.pool.json`, each seed with
  a note and an optional patch (`story-map-pools.md`). This regenerates every pooled seed
  and fails when one no longer passes its mission's acceptance on the first
  generation: **run it after any mapgen change that may move a story map.**
  `-Pcandidates=cordon-line:1-40,heart-burn/frozen:1-20` screens seeds that
  are not pooled yet, on every biome or the one named (reported, never failing). About 30 seconds; writes
  `build/reports/story-map-pools/summary.md`. `commanderEvidence` on
  `cordon-line`, `cordon-road` and `heart` plays the whole pool by default, and
  `createSnapshots -Psnapshot=story-map-pools` renders every pooled map whole.
  Conquest and skirmish stay fully random. Opt-in and excluded from `test`.
- `gradlew.bat crewEvidence` → crews a transport and a capital from their own
  room programs, runs each for four minutes of ship's time, and reports what the
  complement actually spent it doing: the idle share, the activity histogram, the
  longest unbroken stretch of doing nothing, how many are still alive, and which
  compartment purposes hold anybody. Opt-in and excluded from `test` — it costs
  minutes of wall clock and needs two whole hulls to answer its question. The
  standing goal is an idle share of zero, with the resting, socialising and
  exercising columns carrying the crew's off-watch time instead; see
  `CrewLivelinessEvidence` for why the dead count as idle.
- `gradlew.bat shaderEvidence` → everything that needs a **real OpenGL driver**.
  It compiles and links every shader the mod defines, checks that every uniform
  the ground composite uploads exists in the linked program, and runs the state
  brackets against the state the game hands a UI hook. This is the only evidence
  here that runs GL rather than modelling it: the snapshot suites draw through
  Java2D and the shader oracles re-implement the arithmetic on the CPU, which
  proves geometry and is blind to a syntax error, a construct one driver
  rejects, or a renamed uniform — and that last one is silent by specification,
  since `glUniform*` on an unknown location is defined to do nothing.
  Opt-in and excluded from `test`, because it needs an accelerated driver and a
  suite that requires a GPU fails on the machine that has none; where no context
  can be made it reports that it skipped rather than failing. It uses LWJGL 2
  from the game's own install (`HeadlessGl`), deliberately: the mod's render
  classes are written against those bindings, so this runs them rather than a
  re-implementation of them, and it costs no new dependency.
- **A clean context proves nothing about the brackets, which is why the hostile
  fixture exists.** A fresh context starts at GL defaults — alpha writes on,
  ordinary blending, scissor off, no program bound — which are very nearly the
  values `GlStateBracket.applyTextured2DState` sets. Every normalisation in that
  method is therefore a no-op on a clean context, and dropping any one of them
  leaves a clean-context test just as green while the game renders wrong.
  `GlStateBracketHostileStateEvidence` sets the documented incoming state —
  alpha masked out of the colour write mask, a foreign blend function left
  behind by another draw — and asserts both directions: our draw still lands
  correctly, and the caller's state comes back untouched afterwards. It carries
  its own control (the same draw unbracketed) so it cannot pass by measuring
  nothing.
  It also pins that **scissor is inherited on purpose**. The tile brackets leave
  the scissor test alone because a tile pass draws into the game's own target,
  inside the panel rect Starsector has already clipped to, and wants to stay
  there; the FBO brackets disable it because that target is ours and the UI's
  clip rect refers to a different surface. Same reasoning, opposite answer —
  neither is a default worth inheriting by accident.
  **What it still cannot do** is discover a hostile value nobody wrote down in
  it. A fixture encodes what we believe arrives; that residue is the part that
  still wants a live pass.
- `gradlew.bat renderEvidence` → what a frame costs, per layer, on the map the
  owner actually plays. It generates the canonical 560x336 Conquest, a
  280x168 control and the canonical 400x240 colony with four minutes of hive
  creep grown over it, plays them until units, decals, shadows and effects exist,
  and then runs the shipping `BattleRenderer` — the real collect and the real
  drain — into a `HeadlessGl` context at three framings: a compound, a lane, and
  the whole map. Per layer it reports commands collected, quads, sprites and
  custom passes drained, draw calls, texture binds, and the milliseconds
  collection and submission each took, plus a whole-frame wall clock with the
  GPU waited on. `build/reports/render/summary.md` and `summary.json`, and each framing's GL frame as a PNG under `frames/` to diff a lever against its control;
  `-PrenderMaxTicks`, `-PrenderFrames`, `-PrenderDir`, and `-PrenderMaps=body pile,control`
  to profile only the maps whose ids contain those words (a `body pile` of 1,200
  Runner dead, `-PrenderPileBodies`, prices `BodyPileCap`). Opt-in and excluded from
  `test`, for `shaderEvidence`'s reasons — it needs an accelerated driver, and
  it reports that it skipped where no context can be made.
  **Counts must repeat and times must not be read as one.** The task asserts
  that the same world at the same framing collects the identical command count
  every frame, and that **the same fixture stood up twice collects the same
  counts as well**; the times are medians of nine frames after a thirty-frame
  warm-up and carry about 0.06 ms of run-to-run spread per layer. Compare a
  lever against **its own run's control**
  (`-Dbattle.render.doodadAtlas=false`, `-Dbattle.render.creep=false`,
  `-Dbattle.render.cliffLook=false` (with `-PrenderMaps=overlook`),
  `-Dbattle.render.settledBodyMesh=false`,
  `-Dbattle.render.collectCulling=false`), never against a number in a
  document — and never against a delta smaller than that spread.
  **The world it profiles has to be the same world every run**, which it was
  not: the harness plays 600 ticks before it draws, and under the production
  scheduler that landed somewhere different every time — the same fixture on
  unchanged code collected 496, 567 and 537 `UNITS` commands on three runs. The
  frame-to-frame assertion could not see it, because the harness does not
  advance the simulation between frames and so a single run's counts repeat
  perfectly while meaning something else than the last run's. The task forces
  the serial scheduler the way `commanderEvidence` and `simDeterminism` do, and
  the harness asserts that rather than setting it.
  **Thirty warm-up frames, because three was measuring the JIT.** Three covers
  the texture uploads and the batch growth and nothing of compilation: at three,
  the 280x168 control reported `GROUND` at 0.36 ms of collection at a lane
  framing against 0.14 at the whole map — four times the cells through the same
  loop for a third of the cost. At thirty the same framing reads 0.08 and the
  ordering comes right; every figure early in a run fell by more than half.
  Anything measured here before 2026-09-03 was reading that gradient.
  **A lever that can drop something is accepted on pixels, not on counts.** A
  body wrongly culled is silent — the frame is simply missing it and looks
  ordinary — so the run reads back each framing culled and unculled and asserts
  they are identical, and walks a chosen marine out through the viewport edge in
  half-cell steps to put a straddling body there on purpose. Those readbacks run
  after every timed frame on a map rather than between them: eight megabytes off
  the card landed in the next framing's median as noise several times the effect
  being measured.
  Where it stands: **nothing is submission-bound, and nothing collects more than
  the camera shows.** `UNITS` is one to nine draws at every framing on every
  map it profiles (2026-10-23), because the unit atlas takes in whatever the unit
  layers draw rather than a hand-kept list, an image too big to hold whole joins
  as a copy at the size it is drawn at the closest zoom, and additive draws batch
  beside the normal run; what still draws alone is big on screen too and named
  in the **Atlases** section (the creep ooze and spray sheets on The Burn).
  `ROOFS` is one custom pass and one command, and `GROUND` is three
  draws and **no texture binds at all**. Collectors now visit what is in view:
  at a compound framing on the 560x336 map `UNITS` collects 160 commands where
  it used to collect 527 at every framing alike, `COMPOUND` 6 against 98,
  `UNIT_SHADOWS` 18 against 54 — 0.35 ms of our own against the control's 0.47,
  and `UNITS` there is 0.05 ms of collection against 0.12 of drain, so it is not
  collection-bound any more. What is left is `GROUND` at the whole map, 0.27 ms
  over 929 commands that are all genuinely on screen; culling cannot reach that
  and the next lever there is a framing gate or cheaper per-cell resolution.
  `DOODADS` is one bind on every map at every framing (two draws: the props
  through the per-battle decoration atlas, and the doors as one solid mesh),
  where it was up to 2,454 draws over 720 binds; `UNIT_SHADOWS` is one.
  The summary opens with **Atlases** — each atlas's size and fill, and every
  image it left out with the reason (over its cap, no room, refused) — and
  closes with **Where the runs break**: per layer, quads by sheet, what each
  flush was, and every whole sprite by image. Read those before guessing why a
  layer has more binds than it should.
  **The harness does not advance the simulation between frames**, so a resident
  thing's patch cost is charged once, in the frame that catches it up, and every
  later frame in a framing reads as the steady state. That is the shape of the
  instrument rather than a flattering choice — the counts assertion requires a
  still world — but it is why a delta-patched layer reads at a hundredth of a
  millisecond here and should be expected to cost more on the frames a live
  battle actually changes something in.
  **A headless run has no `Display`, and that used to size the composite 1x1.**
  `GroundParallaxPipeline` scales its targets by the framebuffer over the
  reported screen, and off a pbuffer the framebuffer is zero -- so every relief
  target was one pixel, blitted over the viewport as a flat colour. The
  per-layer CPU numbers were honest; the composite's own fill was not measured
  at all, and any picture read back was an average of the frame. A zero display
  now means a scale of one.
  **Our share is not the frame.** The per-layer times are CPU: building the
  stream and handing it over. The whole-frame column exists because a lever can
  trade our cost for the driver's — a resident mesh draws the whole map however
  close the camera is — and every other column here would call that a win.
  **The fast path is `-PrenderMaps=stress`: about 45 seconds, against twenty
  minutes for the whole matrix.** `RenderStressScene` stands a dense battle up
  without a playout: the verdant 400x240 colony at tick zero, a mother bore and
  three bores fed to their tiers with their spore colonies, creep matured at
  once, about 180 bodies round one point (four infantry families, every mech
  chassis on both sides, runners, brutes, spitters and a matriarch) with a
  third of them killed where they stand, 45 ticks so the dead fall and shots
  are in flight, then a fire stepped on its own field to The Burn's shape:
  centred on trees and walls, which alone burn hot enough to throw embers, aged
  45 steps (grass burnt out, crowns and walls at full heat and smoking), plus a
  fresh patch. Its HAZARDS at mid is about 1,360 draws on the old per-sheet
  path (`-Dbattle.render.pagedBatch=false`), near The Burn's 1,323.
  Its counts are asserted to repeat across two stand-ups, as Conquest's are.
  A render-cost fixture only, with no balance meaning. Compare a lever against
  its own control with the same `-PrenderMaps=stress`. A map selection runs
  `RenderBudgetEvidence` alone and skips the creep look evidence that shares
  the tag.
  **Under Nsight Systems, launch the JVM, not Gradle.** Every `renderEvidence`
  run writes `build/nsys/render-evidence.cmd`, which replays that run (its
  classpath, heap, properties and map selection) as one JVM through
  `RenderEvidenceMain`. Gradle under nsys injects into every JVM it forks, and
  its toolchain probe hung there. Capture with this, from the worktree, after
  one `renderEvidence -PrenderMaps=stress`:
  `& 'C:\Program Files\NVIDIA Corporation\Nsight Systems 2026.4.1\target-windows-x64\nsys.exe' profile --trace=opengl-annotations --force-overwrite=true -o "$PWD\build\nsys\stress" "$PWD\build\nsys\render-evidence.cmd"`.
  Both paths must be absolute, because nsys starts the target outside the
  current folder. Extra `-D` arguments to the script go to the JVM. Verified
  2026-10-24: 45,069 GL calls, 37,943 of them `glDrawArrays`, plus GPU workload
  ranges. Each frame is a `KHR_debug` group named `<map> / <framing> / frame N`
  (`GlFrameMarkers`). nsys 2026.4 records the push and pop calls with their
  labels on the OpenGL API row, but builds no KHR range rows from them, so read
  a frame as push to pop: 68, 238 and 162 draws per frame at close, mid and
  whole map. Traced frames run several times slower, so take the times from the
  report, not from the capture.
- `gradlew.bat createSnapshots` → every deterministic visual-evidence suite under
  `build/snapshots/` without launching Starsector.
  It reads art from `mod/` first and the installed game second — the game's own
  order — so vanilla-sourced sprites such as aircraft hulls appear in headless
  frames. The install is already required to build at all (`starsectorDir`), and
  a suite degrades to not drawing those sprites if it is missing. Select
  suites with `-Psnapshot=<id>[,<id>...]` (default `all`, which leaves out
  opt-in look studies such as `deck-props` and `elevation-look`; ids in
  `snapshot-suites.md`) and redirect the common output root with `-PsnapshotDir=<path>`.
  **The pixel work runs on workers** (`SnapshotWorkers`, half the processors;
  `-Dsnapshot.threads=1` runs it all on the calling thread): a review frame
  drains in fixed 32-row bands side by side, particles blend in bands,
  finished frames compress in the background (spilling to a temp file once the
  heap is half full) and a suite's GIFs encode at once. The simulation still
  steps on one thread. `-Dsnapshot.profile=true` prints where a review frame's
  time goes, by phase and by image. `-PsnapshotRawFrames=<dir>` keeps every
  artifact's frames losslessly, and `RawFrameDump.main` compares two such runs
  (maximum and mean channel difference, worst frame side by side) — accept a
  renderer change on that, never on GIFs, whose palette hides and invents
  differences.
  **Review frames are drawn on the GPU by default** through the shipping
  `BattleRenderer` into a `HeadlessGl` pbuffer (`ReviewSceneBackend`), with
  surface relief, sun shadows and event lights the Java2D drain never drew.
  Creep- and fire-heavy scenes render ten times faster or more; small plain
  views are about even. `-Dsnapshot.backend=java2d` is the reference drain;
  `-Dsnapshot.gl.relief=false` drops the relief composite. On the Mac, whose
  game ships an x86_64-only LWJGL 2, the task runs on LWJGL 3 through a
  windowless CGL context instead (`-PsnapshotGl=lwjgl3` does the same on
  Windows); a machine with no context at all says so once and uses Java2D. GL pixels depend on the
  driver, so compare raw frames only between two runs on the same backend and
  machine. See `gpu-review-drain.md`.
- `gradlew.bat battleProbe -Pbattle=<source> -Pscript=<file>` → **ask a battle
  questions instead of bisecting for them.** Stands one battle up, steps it on
  command and answers queries between steps, one JSON line each: `until T`,
  `until-beat final_run`, `entity ID` (health, plate, target, orders, squad,
  telemetry), `near ID R`, `find label=TURRET`, `squads`, `beats`, and, once
  `hitlog on` has been given, `hits target=ID` / `hits-by attacker target=ID`:
  every resolved hit with the penetration, the plate rating and arc it met, and
  what went to armor and to structure. Sources are `thunder-run[:seed=N,...]`
  (the battle `ThunderRunEvidenceTest` plays, built by the same
  `ThunderRunEvidenceBattle`) and `fixture:<path>` for any fixture JSON; without
  `-Pscript` it reads commands from stdin. The battle is deterministic, so
  stepping to the tick that matters and switching the hit log on there costs one
  replay for any number of questions: "who killed the Thunder Run tank" was ten
  seconds here against a bisect of hours. The hit log (`HitLog`, on
  `CombatTelemetryService`) is an observer that draws no random number and is
  off unless asked for. The task gets the same deterministic switches every
  `Test` task does (synchronous routes and influence, serial unit update); a
  JavaExec without them plays the live game's asynchronous battle, which is
  not the one the evidence reported. Add a source in `HeadlessBattles.build`
  when a mission's harness has its own construction: the probe and the snapshot
  suites stand a battle up through `HeadlessBattles` (its registries, strings and
  fixture), so a probe reading reproduces in a clip.
- `gradlew.bat sceneEvidence` → plays the behaviour scenes for their verdicts and
  prints a PASS/FAIL table, one row per loop, with a line beneath each failing
  row saying what was measured. The same scenes already play under
  `createSnapshots`, but rendering is the slow part of that run and **a GIF
  cannot fail**: a scene whose finding has regressed still produces a perfectly
  good animation of the regression. This plays the same catalog with no
  renderer attached, writes `<sceneId>/<loopId>.verdict.json` plus
  `summary.json` / `summary.md` under `build/reports/scenes/`, and exits 1 when
  any verdict failed. Select with `-Pscene=<id>` or a comma-separated list
  (default `all`) and redirect the output root with `-PsceneDir=<path>`. A scene
  that throws while standing its world up fails its own loop rather than the
  run, so one broken scene cannot hide what the others found. Opt-in and
  excluded from `test` / `check`: it plays whole battles, which is evidence.
- `gradlew.bat layerAuthoring` → extensible standalone authoring workbench. The
  Layers page provides drag, scale, rotation, variant-scoped phase-driven
  animation playback, combined-sheet export, the shared snapshot
  catalog, and validated atomic writes to
  `mod/data/appearance/unit-layer-layouts.appearance.json`. Page-by-page detail
  (Turrets, Rooms, Map damage, Tilesets) is in
  `roadmap/battle-render/design/layer-authoring-workbench.md` — read it when you
  need that page's details.
- `tools/authoring.sh <tool> [json]` (or `tools/authoring.cmd`) → call one
  authoring tool and exit. This is the **default** way to reach the authoring
  tools headlessly — list what can be a wall, list/measure/read/write/slice/fit/split/export a tileset, move one piece's cut,
  declare
  or dissolve one of its autotile blocks, render its map-preview comparison, run the snapshot catalog — with no workbench window
  and nothing to start first. `--list` names the tools, `--describe <tool>`
  prints its schema, `--json` returns the structured result. Arguments are one
  JSON object, inline or as `@file` or `-` for stdin; from PowerShell quote the
  `@file` or use `-`, because a bare `@token` is its splatting operator. Exit
  status is 1 when the tool reports a failure and 2 on a usage mistake, and 3
  when the freshness build below failed, in which case nothing was called.
  See the `authoring-tools` skill.
- **Always go through `tools/`, never through `build/authoring/`.** The
  generated launchers run whatever was compiled last, and nothing in their
  output says how old that is. The checked-in wrappers exist to close that:
  each runs `installAuthoringTools` before handing over, so a call cannot use
  classes older than the working tree, and refuses to call anything at all if
  that build fails. It costs about 0.7s per call against a warm daemon (0.85s
  total, against 0.15s for the launcher alone), which is the right trade for
  tools that rewrite hand-authored documents — a stale call has already
  destroyed a hand-cut tileset once, silently, by running a safety guard's
  pre-guard classes. Build chatter goes to stderr; stdout stays the tool result.
- `gradlew.bat installAuthoringTools` → writes the generated launchers under
  `build/authoring/` and prints the `.mcp.json` snippet that registers the same
  tools as an MCP stdio server. The wrappers above run it for you on every call,
  so there is no longer anything to remember after a dependency change or a
  `clean`; run it by hand only to see that snippet. The launchers embed an
  absolute classpath and are therefore generated rather than checked in.
  Register `tools/authoring-mcp.cmd`, not the launcher it execs — the wrapper
  is what rebuilds first. Even so, an MCP server holds its classes for a whole
  session: **after changing tool code, restart the server or use
  `tools/authoring.sh`**, which rebuilds per call. Prefer the shell wrapper over
  MCP registration unless a session already has the server: an MCP stdio server
  must be registered before the session that wants it starts, which is exactly
  the constraint a one-shot command removes. Both entry points are separate
  front doors onto the same domain code, never an embedded server — an editor
  holding unsaved changes and a tool writing the same document would be two
  writers. See `authoring-entry-points.md`.
- `gradlew.bat :asset-pipeline:deriveTileMaps` → bakes `<sheet>_height.png` /
  `<sheet>_normal.png` beside each terrain albedo listed in
  `asset-pipeline/src/tool/resources/tilemaps/tilemaps.json`. **Re-run it after
  re-exporting any tileset that has those companions.** They are found by naming
  convention and sampled at the albedo's own frame coordinates, so a re-packed
  atlas leaves them reading the relief of the sheet they replaced — silently.
  `UrbanTileset3AlphaTest` fails when they drift out of step.
- `gradlew.bat deployMod` → generates the gitignored `mod/sounds/` outputs
  (requires `ffmpeg` on `PATH`) and syncs `mod/` into
  `<starsectorDir>/mods/StarsectorMarines/`.
- `gradlew.bat runStarsector` → deploys then launches the game, running the java
  command line read out of `starsector-core/starsector.bat` rather than the .bat
  itself. The installed .bat ends in a malformed `if errorlevel 1 {` block, so
  `cmd /c` returns that block's status and a JVM that dies mid-battle still
  leaves the task green. Running the command line directly makes the reported
  exit code the JVM's own.
  **The JVM's own output never reaches `starsector.log`.** A fatal-error block, a
  native loader failure, and anything printed outside log4j go to stdout/stderr
  only, so the task tees them to `build/starsector-run/console.log` and points
  `-XX:ErrorFile` at `build/starsector-run/hs_err_pid<pid>.log`. Every run also
  writes `build/starsector-run/run-summary.txt` with the decoded exit status.
  The summary is written by a finalizer, so it appears even when the launch task
  is aborted — a `doLast` is skipped in exactly the cases worth recording, and a
  run that produced no summary at all once left its exit status unrecoverable.
  When the game dies without explanation, read those before `starsector.log` —
  log4j buffers, so a hard kill can drop the log's last lines while the console
  capture keeps them.
  **`-PstockJvm` drops every `-XX:` flag the install carries, plus `-noverify`**,
  keeping heap sizing, `--enable-preview`, the module opens, the system
  properties and the classpath (100 launch args become 32). The installed
  `vmparams` is not stock — it is a community performance file carrying
  `UseAVX=3`, `AVX3Threshold=0`, `-AlignVector`, `EnableVectorAggressiveReboxing`,
  `UseVectorStubs`, `ShenandoahGCMode=iu` and `-noverify`, any of which can end a
  process in ways that skip the JVM's own crash reporting. It is a control for
  "is it the flags?", not a recommendation; bisect them only once that answers
  yes. The summary records which mode ran.
  **The exit status is the fact that separates the cases**, which is why it is
  written to a file rather than only logged. `0` means something asked the
  process to stop, so a `0` with no shutdown banner in the log means it was
  killed from outside the JVM. An NTSTATUS names a cause instead:
  `0xC0000005` is a native crash, and `0xC000001D` (illegal instruction) points
  at the experimental `-XX:UseAVX` / vector flags in the installed `vmparams`
  rather than at anything in the mod.
- `gradlew.bat packageRelease` → the public download,
  `build/release/StarsectorMarines-<version>.zip`, one top-level
  `StarsectorMarines/` folder: `mod/` without its generated folders, plus the
  shaded jar (after `verifyModJarBoundary`) and sounds transcoded fresh into
  `build/release/sounds`, so stale clips in `mod/sounds` cannot ship. It fails
  when `mod_info.json` and `build.gradle` disagree on the version. Every debug
  surface is behind `DevConfig.DEV_MODE` (`-Dmarines.dev=true`), which
  `runStarsector` passes and a player's install never has;
  `runStarsector -PplayerBuild` launches without it. See `v0.1-public-build.md`.
- **Shipped textures are fitted to how they are drawn** (`texture-fit.md`).
  `optimizePngs` (fed to `packageRelease` and `deployMod`; `-PfitTextures=false`
  deploys the sources) downscales each image to the largest size the usage
  record `art-source/texture-usage/usage.json` says it is drawn at, keeps
  unmeasured images whole, and reports to `build/reports/texture-fit/report.md`.
  **Load every mod texture through `ModTextures`** (`sprite`, `loadTexture`,
  `readImage`), never `Global.getSettings().getSprite`/`openStream` directly:
  code addresses images in source pixels, and only `ModTextures` maps a fitted
  image back to them. Grow the record with `-PtextureUsage` on `createSnapshots`
  or `commanderEvidence` (plus a dev-mode game), then `mergeTextureUsage`.
- `gradlew.bat prepareCatalogSmoke` → stages the additive two-provider catalog
  acceptance fixture without launching Starsector. Pass
  `-PcatalogSmokeMode=collision` to stage the duplicate-id variant.
- `gradlew.bat catalogSmokeLive` → explicitly launches the installed Starsector
  executable against the isolated staged fixture, captures application-load
  evidence, and terminates only its launched process tree. It redirects mods,
  saves, screenshots, and logs under `build/catalog-smoke/`; use only when a
  live game-runtime acceptance pass is intentionally required.
- **Blender renders go through the render queue**, not a session's own shell:
  `python tools/render_queue.py submit job.json --commit HEAD`, then
  `status [--job <id>]`. The queue is `render-queue/` on the shared Drive. A
  runner on each machine (a Windows scheduled task; a LaunchAgent on the Mac) runs
  one job at a time and swaps finals out at a frame boundary for review and
  light jobs. Gated lanes run only while that machine's gate is `run`. The
  `light` lane (half size, ≤16 samples) is not gated. The gate is manual and
  belongs to the render coordinator role, which changes it only on the
  owner's word. Job format, inputs, and collecting output: the `render-queue`
  skill.

### Visual snapshot workflow

**Every preview shown to the user must be rendered at 1920x1080 with 100% UI
scaling.** Verify both settings before displaying or opening a preview. Other
resolutions and UI scales may be checked internally, but must not be shown to
the user. Resizing an image rendered at another UI scale does not satisfy this
rule.

`createSnapshots` is the only top-level task for deterministic visual evidence.
Do not add per-domain Gradle tasks such as `renderTurretPreviews`; add a
`SnapshotSuite` provider to the shared catalog instead. Both the Gradle task and
the layer-authoring workbench consume that catalog, so a registered suite is
available from automation and the editor without a second integration path.

Suites register through `META-INF/services`; `tools/authoring.sh snapshot_list_suites`
lists every id. `snapshot-suites.md` describes the notable ones and where they write —
read it when you need a particular suite, and add a row there, not here.

Run all suites with `gradlew.bat createSnapshots`. Use
`-Psnapshot=<id>` for one suite or a comma-separated selector for several; quote
the whole property in PowerShell, for example
`'-Psnapshot=layers,turrets'`. `-PsnapshotDir=<path>` changes the shared output
root while retaining the per-suite subdirectories. Command-line generation
replaces matching files without prompting and does not remove stale ones, so a
suite that renames an artifact leaves the old name behind until it is deleted.

A `-Dbattle.*` switch on the command line reaches the Gradle daemon and stops
there. `createSnapshots` and every `Test` task forward them into the fork, so a
**control run** — the same evidence with one routing or pathfinding layer
switched off — is one command away:

```powershell
.\gradlew.bat createSnapshots '-Psnapshot=killing-ground' '-Dbattle.pathfinding.casualtyRouteCost=false'
```

Reach for that rather than checking out an older commit. A commit-to-commit
comparison measures every other difference between the two trees at the same
time, which is how a seven-thousand-tick swing in the Conquest matrix was once
credited to a squad behaviour and turned out to be another session's
reinforcement work arriving on a merge.

**A switch added for an A/B is temporary** (owner, 2026-10-23). Switches pile
up because nothing retires them: an audit that day found about 260 `-D` reads
in `src/main`, most of them settled comparisons whose losing arm was still
carried as dead code. So:

- A new `-Dbattle.*` (or `-Dmarines.*`) switch is temporary unless you say
  otherwise. Its property constant's Javadoc states its **removal condition**:
  which comparison decides it and what reading settles it.
- Every switch is listed in `switches.md` (`roadmap/switches.md`) with its kind,
  date and removal condition. `SwitchRegistryTest` fails on a `battle.*` or
  `marines.*` property read in `src/main` that the registry does not list, and
  on a registry row whose switch no longer exists.
- The session that added a switch removes it, together with the losing code
  path and any tests of that arm, as soon as the comparison is decided. Do not
  leave a hard-coded `true` behind.
- A permanent lever (real configuration, a determinism or concurrency switch,
  or a standing control that an evidence task or scene deliberately runs) needs
  a stated reason in its registry row.

A suite artifact is a PNG or, for a suite whose evidence is a played battle
rather than a composition, an animated GIF built from a frame sequence. The
runner writes both; a suite never writes files itself. Animated review frames
come from `BattleReviewFrameRenderer`, shared with commander evidence so the
two do not drift into separate camera fits and marker palettes.

#### What a review frame draws, against the live screen

`BattleReviewFrameRenderer` collects the **production** `BattleRenderer` layers
and drains them through a Java2D backend (`HeadlessBattleSceneRenderer`), so a
layer that goes through the draw list is drawn the way the game draws it. What
differs is the part of the live presentation that is not in the draw list: GL
custom passes, and services the screen ticks every rendered frame. A feed
(`ReviewImpactFx.feed`, called once per simulation tick) owns that second half
and drains the same simulation lists `BattleScreen.advance` does.

| Live layer / feature | Review frame |
|----------------------|--------------|
| Ground, decals, doodads (sandbag, fence, trench, rubble), parked vehicles | Drawn. Terrain relief, sun shading and normal-mapped lighting are the GL composite and are not drawn; `sun-shadows` holds the CPU model |
| Units, bodies, health bars, medic and satchel markers | Drawn |
| Unit shadows, roofs and fort building art | Drawn when asked for (`withUnitShadows`, `withRoofs`); off by default so bodies stay readable |
| Aircraft hulls, turrets and shadows, warships, convoys, drones | Drawn |
| Aircraft engine plumes (`EngineFxPass`) | Drawn (additive, replayed through recording sprites) |
| Projectile bodies, hitscan tracers, bolts, tracer tails | Drawn (`SHOTS`) |
| Missile and projectile contrails (`ContrailFxService`), and warship missiles' contrail ribbons | Drawn as flat-coloured ribbon segments through the shared `RibbonBatch` geometry; additive plume styles draw as ordinary blends |
| Lingering beams and delivered-ordnance traces (falling bombs, gun-run streaks) | Drawn |
| Weapon `fx` blocks: launch, muzzle, trail, impact, aftermath; heavy blasts, rolling blasts, wall dust, wreck smoke and fire; point-defence intercept bursts | Drawn from the real `ImpactFx` fed per tick, painted over the frame |
| Smoke clouds, shield domes, point-defence engagement marks, demolition packs, tender sites | Drawn (shield domes by the CPU model of their shader, in blocks) |
| Night lighting pools | Drawn. Muzzle, impact and fire lights (`GroundLightService`) are not fed, so a night battle's shots do not light the ground |
| Capture-point rings and ticks, objective annuli | Drawn |
| Objective, compound and fort **text** labels (GL bitmap font runnables) | Not drawn; reviews name places with `ReviewAnnotations`. Mission-marker and salvage cards are drawn as labels but the mission layer is left out on purpose |
| Fog of war | Not drawn, and hides no body: a review is a neutral observer (`BattleRenderHostProfile.OBSERVER_SCENE`), so an enemy nobody on the field can see is still drawn |
| Highlights, selection rings, order lines, zone and convoy debug overlays | Not drawn: UI and debug |
| Ship-deck lighting (`DECK_LIGHTING`) and deck scenes | Not drawn here; the ship-deck suites have their own renderer |

A frame is a pure function of simulation state and the feed's history: the feed's
jitter is seeded, and the animation clock is pinned (`REVIEW_CLOCK_SECONDS`) so
no wall-clock pulse reaches the pixels. The drain's bands are a fixed height
rather than one per worker, because where a band starts is in the picture: a
band restarts Java2D's fixed-point sample walk through a scaled or turned
sprite, which can move a channel by a level or two on that row — and, very
rarely, include or drop one edge pixel of a turned sprite
(`HeadlessUiRenderer.drawInBands`). Fixed bands keep every frame the same on
every machine and at every worker count; `-Dsnapshot.bandRows=0` drains each
frame whole, bit for bit what the serial drain drew. Scenes built over a bare grid get no
doodads unless they hand the generator's `ctx.doodads` to the simulation; the
fort scenes do not, so their frames show none of a fort's earthworks
(`render-parity` hands them over).

### Behavior scenes

Per-scene histories (`FrontageScene` through `LateArrivalScene`) are in
`roadmap/battle-render/design/behavior-scenes.md`; read it when you need a particular scene.

**A scene's verdicts assert mechanics; outcomes are readings.** Features are
still landing, and every one of them moves who wins a fight: a verdict such as
"the wall came down", "the company took the bunker", "a raider was shot down" or
"at least N defenders died" goes red on an unrelated merge, or on a seed change,
without anything it was written to protect having broken. Assert what the
machinery does — the plan gave the breach to squads that carry the kit, a
breacher with the wall in reach and kit ready spent it within a few seconds, a
planted charge damaged the wall it was set on, the holder follows the flip,
nothing is fired at your own side — and where the mechanic needs a precondition
the fight may not supply (a breacher alive at the wall), make the verdict
conditional on it and say so in the detail rather than demanding the precondition.
Wall-down ticks, rounds, kills, losses and who held what at the end go in the
report's metrics, where a person reads them. A scripted, deterministic effect
(a placed barrage on a fixed target, a bomb on a nest) may still be asserted
as a mechanic; an unscripted fight may not. `assault-muster` takes
`-Dbattle.scene.assaultMuster.seed=<long>` so a verdict's seed-stability can be
shown rather than assumed.

Prefer a scene over a mission harness whenever the question is about one
behavior rather than about a whole battle's balance, and add another scene
rather than widening this one past what its name claims.


Snapshot generation is tool/test infrastructure and must not enter the shipped
mod jar. Keep reusable catalog and runner code in `:layer-authoring`, keep
mod-specific providers in the root test source set, register providers through
`META-INF/services`, and keep renderers deterministic and independent of a
Starsector process or OpenGL context.

`gradlew.bat verifyModJarBoundary` enforces that, and `check` depends on it.
It computes the forbidden set — everything on the tool runtime classpath that is
not also on the shipped one, which is the workbench, the MCP host, JUnit and the
game's own jars — and fails if any of it is in `StarsectorMarines.jar`. Nothing
to keep in step: a new tool dependency is covered the moment it is added.

The `layerAuthoring` workbench's **Snapshots** tab invokes the same catalog in
process. It renders off the Swing event thread, confirms before replacing PNGs,
and refuses to create `layers` evidence while the authoring document has unsaved
changes. It can render all suites or one selected suite and, like the command,
does not remove obsolete PNGs from earlier runs.

The workbench discovers top-level authoring pages through `AuthoringPageProvider`
services on the tool runtime classpath. Keep the generic host and lifecycle in
`:layer-authoring`; keep mod-domain pages such as Turrets in root tool/test
sources so the shipped mod jar and the generic tool module do not acquire each
other's domain dependencies.

## Tests

**A unit test tests a unit.** It exercises one class or one function
directly, on the smallest input that can show the mechanism is right. If it
has to stand up a world generator, a battle, or a renderer to ask its
question, it is not a unit test and does not belong in `test` — whatever it
is measuring, there is a unit underneath it that can be asked directly.

The failure mode is proving the *case* instead of the *core*. A fill-quality
question is about the fitting and the floor it fills, so it is asked of
those. Generating five hulls at six seeds to look at the result establishes
nothing the one fitting did not, fails for reasons belonging to the inputs
rather than the code, and costs ninety world generations on every run by
every concurrent session forever. The arithmetic hides: five times six times
three reads like one test.

**Every new test must complete in under one second.** This is a rule, not a
consequence: the full `:test` suite now takes about ten minutes, which means
nobody runs it, which means it protects nothing. A test aimed at one unit is
small because the unit is; one that cannot finish in a second is asking a
whole-system question and is in the wrong place. Measure it (the JUnit XML
under `build/test-results/test/` records each test's time) before
committing, and do not raise the bar for a test that misses it — move the
question down to its unit, or move the test out of `test`.

**Test less, and only meaningful core behaviour** (owner, 2026-10-21). The project
wants fewer tests, not more: test the rules the code owns, not the content it ships.
A test that asserts what an image, a data file or a tuned number currently contains
(a pixel window, a sprite's size, a curve's constants) fails whenever the content is
reworked while nothing is broken; do not write one, and delete one you find failing
for that reason rather than updating it to the new content.

**Minimize the necessity of end-to-end tests** (owner, 2026-10-23). Before reaching
for a whole battle, ask whether the change can be proved at the unit or
integration level, and structure the code so it can: a rule that guarantees an
invariant is tested where it is decided, and a change whose failure mode is a
logged fallback (a hitch, never a wrong picture) is measured, not gated on a
played battle. A battle run costs its own session minutes and slows every other
session on the machine; the more sessions simulate whole battles, the slower
development is for all of them.

**Things that are not tests do not go in `test`:**

- **Mission and battle runs** — anything that plays a simulation forward for
  its outcome, stands up a mission, a world generator or a commander, or
  replays a fixture. That is evidence: it belongs in `commanderEvidence`,
  `sceneEvidence`, `loadoutMatchup`, `generatedSpaceEvidence` (for an assertion that
  needs a generated world to ask) or a new opt-in task of the same shape.
- **Snapshot and rendering work** — anything that renders a frame, a screen,
  a GIF or a contact sheet, or compares pixels. That belongs in a
  `SnapshotSuite` under `createSnapshots`, or in `shaderEvidence` /
  `renderEvidence` when it needs a real driver.

Measurement that genuinely needs the whole space is **evidence, not a
test**, and belongs in an opt-in Gradle task excluded from `test`, the shape
`commanderEvidence` and `createSnapshots` already use. Existing slow tests are
not grandfathered as a pattern to copy: when you touch one, move it out or cut
it down rather than extending it.

## Mod layout

For new modular terrain/cover art and damage states, use the geometry-first
ImageGen workflow in `art-source/geometry-first-imagegen.md`: code owns the
silhouette and assembly contract, ImageGen reskins the retained guide, and
the ordinary exporter produces shipped assets.

The `mod/` folder in this repo is what ships. Pre-pack art inputs — raw
generated sheets, ImageGen masters, retained `sources/` originals, tileset
authoring documents, and the scripts
that derive shipped art from them — live under `art-source/` instead, because
`deployMod` is a `Sync` of the whole `mod/` folder and would otherwise copy them
into every install. `RawArtStaysOutOfModTest` enforces that boundary; see
`art-source/README.md`. `mod_info.json` lists the jar at
`jars/StarsectorMarines.jar`. The `modPlugin` entry point is
`com.dillon.starsectormarines.StarsectorMarinesModPlugin`.

## Starsector API conventions

- Compile-only deps (never bundle into the jar): `starfarer.api.jar`, `starfarer_obf.jar`,
  `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`, `xstream-1.4.10.jar`,
  `fs.common_obf.jar` — all live in `<starsectorDir>/starsector-core/`.
- API sources are in `<starsectorDir>/starsector-core/starfarer.api.zip` — unzip locally
  for IDE attachment, do not check in.
- Logging: `Global.getLogger(Class)` returns a log4j 1.2 `Logger`. Game logs to
  `<starsectorDir>/starsector-core/starsector.log`.
- The `BaseModPlugin` lifecycle: `onApplicationLoad` (once at game start, before any save),
  `onNewGame`/`onNewGameAfterEconomyLoad`/`onNewGameAfterTimePass`, `onGameLoad(newGame)`
  (every load), `beforeGameSave`/`afterGameSave`.
- Faction definitions: `mod/data/world/factions/<id>.faction` (JSON despite the extension).
- Hulls/variants: `mod/data/hulls/`, `mod/data/variants/` mirroring vanilla.
- Strings (for i18n): `mod/data/strings/strings.json`.

## Doc-driven development

`roadmap/` records the enduring model and the implementation work that has not
shipped yet. It is not an archive of implementation history. Code, tests, Git,
and focused Javadoc are the authority for how shipped behavior is implemented.

### Noun docs are the canonical feature model

Each coherent feature/domain has one canonical noun doc, normally
`roadmap/<feature>/design/<feature>-nouns.md`. A narrow child feature that does
not own a separate model cites its parent's noun doc instead of restating it.

The noun doc is the expanding, high-level explanation of how the feature fits
together. It owns:

- the domain vocabulary and the distinction between easily-confused concepts;
- relationships, ownership, authority boundaries, and end-to-end flow;
- standing behavior, invariants, and design laws that future work must preserve;
- boundaries with adjacent features and the durable extension points.

New concepts must fit or amend the canonical vocabulary rather than create a
parallel noun in a story or implementation. Read the applicable noun doc in
full before planning, implementing, reviewing, or changing a story in that
domain.

Keep implementation detail out of noun docs: no per-class inventories, field
layouts, method contracts, file-by-file code maps, chunk logs, dated progress
notes, or commit narratives. Those details belong in code, tests, Javadoc, and
Git. A noun doc may cite a stable code symbol when that helps a reader find the
implementation, but it explains the system rather than duplicating it.

### Roadmap structure

Feature directories under `roadmap/` converge on this layout:

```
roadmap/<feature>/
  design/
    <feature>-nouns.md  — canonical vocabulary, relationships, laws, and flow
    stories.md          — concise board of open work only
    shipped.md          — one-line ledger of folded and deleted stories
    *.md                — enduring rationale or direction that does not fit the noun doc
  stories/              — one temporary doc per active/planned implementation unit
  assets/               — optional design or acceptance evidence
```

The two documentation buckets are distinguished by kind and lifecycle:

| Bucket | Holds | Lifecycle |
|--------|-------|-----------|
| `design/` | Noun docs, enduring architecture/rationale, direction docs, the open-work board, and the shipped ledger. | Living reference; not a unit of work and never moved merely because an epic ships. |
| `stories/` | A discrete, shippable implementation unit with scope, constraints, acceptance, and a plan. | Exists only while the work is planned or in progress; folded and deleted when it ships. |

Create broad system direction in `design/`. Create a concrete task that is
about to be implemented in `stories/`. A feature with more than a couple of
open stories keeps `design/stories.md` as the single cold-start board: open
stories and their one-line state only. Shipped rows leave the board.

`next-session.md` is not part of the target structure. The board says what is
available, the active story says what remains inside its scope, and Git records
the history. Do not create new handoff journals or append session changelogs to
living design docs.

### Retiring a shipped story (there is no live `complete/` bucket)

A shipped story is folded into the canonical docs and deleted, never archived
in place. Sort its content by what remains useful:

| Story content | Destination |
|---------------|-------------|
| Standing feature semantics: vocabulary, relationships, authority, behavior, invariants, and boundaries | Integrate into the existing sections of the feature's noun doc. Do not append a story-history section. |
| Enduring rationale: alternatives considered, rejected directions, capacity bounds, or a decision whose reason is not evident from the resulting model | Integrate into the appropriate `design/` doc, creating one only when the rationale has real continuing value. |
| Implementation detail and work log: class/field mechanics, chunk tables, progress notes, commit chains, landed-vs-planned narration | Do not copy into roadmap docs. Keep the implementation legible in code/tests/Javadoc and let Git retain the work history. |

Before deleting the story, run `git grep -F '<slug>.md'` and redirect every
citation to the noun/design section that absorbed its standing content. Then,
in the same commit:

1. fold the durable content;
2. remove the story from `design/stories.md`;
3. add one row to `design/shipped.md` with slug, ship date, commit ref(s), and
   fold destination;
4. delete the story doc.

`design/shipped.md` is a ledger, not a narrative: one row per shipped story,
never a section-sized retrospective. Git can recover the deleted source when
needed. Existing `complete/` folders are legacy migration input, not a live
destination; never add a newly shipped story to one.

### Document status and freshness

- Every story starts with `Status:` and `Written: YYYY-MM-DD`. Add or replace a
  single `Updated: YYYY-MM-DD — <brief freshness note>` line on material
  revision; never accumulate update history in the doc. A story never rests at
  `COMPLETE`—shipping means fold, ledger, and delete.
- Every enduring design doc starts with a `Status:` describing the direction,
  not an implementation task: `ACTIVE`, `SHIPPED`, `SUPERSEDED by <slug>`, or
  `DRAFT`. It remains under `design/` when shipped. Use the same `Written:` and
  single-line `Updated:` convention.
- Keep `roadmap/README.md` current focus and immediate next-up honest. It is the
  project-level orientation, not a second feature board or a shipped-work log.
- **Update docs at commit boundaries.** A change that alters the standing model
  updates its noun doc in the same commit. Do not accumulate documentation debt
  across story commits.

### Cross-references: bare slugs, not relative paths

- **Reference another roadmap doc by bare slug in backticks** — `` `central-keep.md` ``,
  not `[central-keep.md](../central-keep.md)`. A slug survives the
  bucket reorganizations a live doc may undergo, and
  `git grep -F '<slug>.md'` finds every citation in one sweep before a story
  is folded and deleted. Look docs up by name (fuzzy file-open), not by path.
  Filenames are stable slugs — status lives in the bucket and the doc's own
  status line, never in the filename. A bare slug does not excuse a dangling
  citation: redirect it when its story is retired.
- **Reference code by backticked symbol, not a path link** —
  `` `TacticalScoring.hasReachableFiringSpot` ``, not a `../../src/main/...`
  link. Package moves are routine here; the symbol name is what stays true
  and what you would actually search for.
- **Reference project memory as `[[slug]]`** — e.g. `[[battle_services_systems]]`.
  The memory directory lives outside the repo, so a relative link can never
  resolve.
- **When you fold and delete a doc, redirect its citations in the same commit.**
  `git grep -F '<slug>.md'` across `roadmap/` *and* `src/` — package-info
  charters and javadoc cite roadmap docs too. A fold that leaves dangling
  citations has traded stale docs for broken ones.

(Convention adopted 2026-08-22 from the sibling MoonLightEngine project,
after a link sweep found 41 rotted references — nearly all of them paths to
docs that had merely moved between roadmap buckets.)

## Conventions for this repo

- Package root: `com.dillon.starsectormarines`.
- Mod ID: `starsector_marines` (snake_case is the Starsector convention).
- Version in `mod_info.json` and `build.gradle` should match.
- Do not edit anything under `C:\Program Files (x86)\Fractal Softworks\Starsector` — it's
  read-only reference. Vanilla files there are the canonical examples for data schemas.

## Code style

- **NEVER write an inline fully-qualified name. Use `import` + simple name —
  always, in every line you write or edit.** (`Vehicle v`, not
  `com.dillon.starsectormarines.battle.vehicle.Vehicle v`.) The ONLY exception
  is a Javadoc `{@link}`, where an FQN is fine and needs no import. This is
  unconditional for new/edited code: **do not "follow context clues."** If the
  file you're editing is full of inline FQNs, you still add an import and use the
  simple name for your additions — match the project style, never the file's bad
  habit. (Two carve-outs that are about *not touching other code*, not about
  writing FQN: don't do sweeping FQN→import refactors of existing files unasked,
  and a mechanical package-move rewrite that merely preserves a file's existing
  FQNs is fine.)

## Committing

Linked worktrees give each session its own working tree and index, but commits
should still stay narrowly scoped:

1. Stage explicit paths only — `git add <path> …`, never `git add -A`/`.`.
2. Review `git status` and the staged diff before committing.
3. Commit only the current task's files. Leave unrelated files and other
   sessions' work alone.
4. Never `git stash`; it complicates ownership and recovery across worktrees.

### Commit-command mechanics (these have bitten before)

- **`-m` goes BEFORE `--`.** `git commit -m "msg" -- <path> …`. Anything after
  `--` is a pathspec, so `git commit -- <path> -m "msg"` makes git treat `-m`
  and the message as filenames (`pathspec '-m' did not match any file(s)`).
- **Match the shell to the tool.** The here-string for multi-line messages
  (`-m @'…'@`) is **PowerShell only** — use it in the PowerShell tool. The
  **Bash** tool reads `@'…'@` literally and mangles the commit. In the Bash
  tool, pass the message as a normal double-quoted string (`-m "line1
  line2"`); avoid backticks and `$` in it, or single-quote. Pick one tool per
  commit and quote for that shell.
- A failed-pathspec error means **nothing was committed** — fix the flag order
  / quoting and re-run (the `git add` already staged the files; don't re-add).

## Workspace location

Sessions normally start in the main workspace at
`C:/Users/Dillon/IdeaProjects/starsectormarines`. Read-only tasks may remain
there. For tasks that change files, follow the session worktree workflow above
and run all task commands from `C:/Users/Dillon/IdeaProjects/starsectormarines/.claude/worktrees/<unique-name>`.

## Multi-project layout

- `:` (root) — the mod itself. `src/main/java` holds `StarsectorMarinesModPlugin`, the
  bridge intel plugin, the scene-graph renderer.
- `:asset-pipeline` — vendored copy of MoonLight Engine's asset code. Two source sets:
    - `main` (runtime) — MeshData, LoadedModel, MaterialInfo, Animation, Skeleton, Bone,
      BvhParser, AnimationRetargeter, ModelSerializer. **Bundled into the mod jar via
      fat-jar.** Depends only on JOML at runtime. No Lombok, no Log4j 2, no LWJGL 3.
    - `tool` (build-time importer) — ModelLoader, MeshExtractor, MaterialExtractor,
      AnimationExtractor, BoneRemapConfig, ProcessModelsTask, ConventionNormalizer,
      AssetConventionConfig. Uses Assimp + LWJGL 3 + Log4j 2. **Never ships.**
      Invoke via `gradlew :asset-pipeline:processModels`.
- The mod's `jar` task pulls in `:asset-pipeline:main` outputs + JOML via the
  runtime classpath, producing a single fat jar at `mod/jars/StarsectorMarines.jar`.
