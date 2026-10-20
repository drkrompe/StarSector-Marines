# Second Wind arrival cinematic

**Direction: [storyboard v3](storyboard-v3.md)** (draft). The arrival is being
rebuilt image-first: layered high-resolution stills animated only by parallax and
push-in, with each shot kept only if it pays off for the story or the player. It
adds a voiced Hegemony traffic-control hail over the space shots.
[Storyboard v2](storyboard-v2.txt) is superseded.

The packages below are the earlier animated sequence (**space -> entry -> forest
-> fields -> port -> landing**), reviewed in the
[28-second sequence](arrival-sequence/README.md) and its
[movie](arrival-sequence/review/jangala-space-to-berth.mp4). v3's salvage table
says which of them feed the new plates.

## Where it stands (2026-10-19)

The shipped cut is **C1 -> C2 -> entry -> C3 -> C4 -> C5** (about 2:17 with the
title). Every shot's content is owner-approved; what remains is the final renders,
the final mix and packaging into the game.

| Shot | Length | Source | State |
|---|---|---|---|
| C1 approach | 37.7 s | `corvus-approach/` (`c1v2_scene.py`, `c1-v2-shot.md`) | All ships on final builds; half-size motion check approved. Planet lock and captions are runtime data (`cinematic_export.py --shot c1`). |
| C2 bridge | 15 s | `patrol-abeam/` (`c2-layout.md`, `c2_bridge_render.py`) | Camera set approved (walk, duck, breath, frame, lean, lens). Final Second Wind and Broadswords wired. Glass reflection kept faint, bridge lit brighter. Castle passes once at 5.5-6 s. Renders the Second Wind build pinned in `c2_bridge_render.SW_PIN` (`pins/`); a new build means moving the pin. |
| Entry | 7 s | `patrol-abeam/compose_entry.py` | Approved. |
| C3 window | 16 s | `first-open-sky/` | Approved. |
| C4 landing | 21.4 s | `industrial-exterior/c4x_scene.py` (`LATE` 5.4: hover 12.0, touchdown 16.4) | Landing dust approved. Darker pads, final Second Wind. A joined C4+C5 review with the mix is the last check before the final render. |
| C5 yard and title | 40 s | `industrial-exterior/c5_scene.py`, `c5_title.py` (title from 24 s, held to 38, black by 39.5) | Approved: 40 s length, dark pads, families walking out from under the hull, only the Colossus in the air under the title (lift-off dust, slow turn, mains at 19.5 s, engine-wash cloud). The title is added on Windows (the Mac has no install font). |

**Sound and script.** `arrival-audio/` owns sound, music and voice. Castle's script
was rewritten and approved (`arrival-audio/sfx-cues.md`): hail 01-08 unchanged, hail
09 dropped, five new Castle lines (entry, C3, C4, C5 PA, C5 closer). Music: strings
for C1-C4, Ending A (strings and piano) for C5. The Arrival Score Desk artifact plays
the whole cut with the current mix; the owner's desk settings still need to be turned
into the final mix. The game data already carries the new lines (`mod/data/cinematic/jangala-arrival/`,
strings, sounds).

**Open work, in order.**
1. Owner review of the joined C4+C5 (half size, with `review/cinematic-c4c5/2026-10-19_mix-castle-v4_windows/01-c4c5-mix.wav`).
2. Final mix from the owner's score-desk settings.
3. Final full-quality renders of C1, C2 (lit), C4 and C5. C5's title is laid on Windows by `c5_title.py`.
   Final render state (2026-10-20, 03:00): **C5 done** (Drive `builds/c5-final/9e98802f0/` frames, titled master `builds/c5-final/c5-final-titled-9e98802f0.mp4`). **C2 rendered** (frames `builds/c2-final/frames/`, movie `builds/c2-final-bridge-9dc05a265.mp4`) but with the faint glass the owner has since rejected: it is re-rendered overnight (see Overnight renders). **C1 in progress**, split: the Mac renders frames 0-649 (`builds/c1-final/9e98802f0/`), Windows 650-904 in `build/cinematic/c1-corvus/frames/` of the c1-corvus-v2 worktree (copy them into the same Drive folder when done; if a run is stopped mid-frame, delete any 0-byte PNG placeholder before resuming or that frame is skipped); late frames are heavy (2-3 min each), so both halves finish around midday. **C4 waits on the owner's look at the joined C4+C5 check**, then renders on the Mac like C5. Entry and C3 need C3's layer build first (`first-open-sky/build/layers/` is a generated input: run the C3 layer and plate scripts, then `compose_entry.py` and `compose_c3.py` at full rate).
4. Packaging: the in-game C5 composer still builds an 8 s shot and must take the 40 s one; the new voice files need `processAudio`; then re-export every shot (`cinematic_export.py`) and run `ArrivalCinematicStringsTest`.

## Overnight renders (owner, 2026-10-20)

Full renders run **overnight only**. During the day the GPUs are the owner's: only half-size
stills for look questions. Every render below can be stopped at any time and resumed later
without losing finished frames.

**Started by hand only (owner, 2026-10-20 10:15).** Nothing starts a render on its own: the
owner asks the coordinating chat to start one (at night, or over a break such as lunch) and to
pause it again. The *Starsector C2 overnight* task is **disabled**; it would have started C2
before C1's Windows half was finished. State at the pause: Windows C1 has 650-792 on disk
(793 removed as a 0-byte placeholder), its render stopped; the Mac has 0-525, its render
**frozen** with `c1-pause.sh` (continue with `c1-resume.sh`; it holds its memory until then).

**The queue, in order:**

| # | Shot | Machine | What | Cost |
|---|---|---|---|---|
| 1 | C1 650-904 | Windows | Finish the remaining frames. 650-790 are already copied to Drive `builds/c1-final/9e98802f0/`; tonight's C2 job copies the rest (copy, never move: the render skips frames on disk). | 2-3 min a frame |
| 2 | C1 0-649 | Mac | Finish its half (0-521 done at 10:30). Frames stay in the Mac's `render-c1-9e98802f0` checkout; a watcher copies all 650 to Drive `builds/c1-final/9e98802f0/` once every one exists. | about 200 s a frame, about 7 h left |
| 3 | C2 | Windows, after 1 | **Queued** (owner passed the glass, 2026-10-20). Full re-render with the glass at the model's own reflection (8122dc22d). The script still does the whole job when run by hand once C1's Windows half is done (`powershell -File build/overnight/c2-overnight.ps1`; it stops itself at 08:00). The disabled scheduled task *Starsector C2 overnight* runs `build/overnight/c2-overnight.ps1` in the c1-corvus-v2 worktree daily at 22:00: it waits for C1's Windows half, renders to `build/cinematic/c2-bridge/lit-glass/` with `--resume`, stops itself at 08:00 and resumes the next night, then builds the movie and copies frames and movie to Drive `builds/c2-final-glass-8122dc22d/`. Log: `build/overnight/c2-overnight.log`; `done.txt` there when finished. Then delete the task (`schtasks /Delete /TN "Starsector C2 overnight" /F`). | about 4.3 h at 64 samples |
| 4 | C4 | Mac, after 2 | Only once the owner has passed the joined C4+C5 check. Renders like C5. | EEVEE, short |

**Pausing and resuming.**
- *Pause anything on Windows:* stop the Blender process (close its terminal, or
  `Stop-Process -Name blender` in PowerShell, which stops every Blender on the machine).
- *Resume C1 on Windows:* delete any 0-byte PNG in `build/cinematic/c1-corvus/frames/` of
  the c1-corvus-v2 worktree (a 0-byte file is the frame that was in progress; left in place,
  it is skipped). Then, from `corvus-approach/`:
  `"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python-exit-code 1 --python c1v2_scene.py -- --final --frames 650:904`.
  Frames already on disk are skipped.
- *Resume C2:* rerun the same `c2_bridge_render.py` command with `--resume` and the same
  `--out`, with `C2_LEGION_ASSET` set. `meta.json` in the output folder lists finished frames.
  Give `--out` as an **absolute path**: Blender resolves a relative one somewhere else (a
  half-size check landed in `C:\build\`).
- *Mac:* scripts in `~/render-jobs/`. `c1-pause.sh` / `c1-resume.sh` freeze and continue
  Blender mid-frame (nothing lost; it keeps its memory and GPU, and the Mac must not log out
  or reboot while paused). `c1-stop.sh` kills it; `c1-restart.sh` clears 0-byte placeholders,
  relaunches `--final --frames 0:649` under caffeinate and restarts the copy watcher (about
  4 min of setup). Log: `~/render-jobs/c1-final.log`. Or ask the Mac render queue session.

**Ways to render faster.** The estimates below are untested. Measure each one on a single
half-size still of a heavy frame against the current settings before using it on a final.
Change settings only between shots, never partway through one, or the two halves won't
match.
1. *C1 volumes* (the late frames are slow because of plumes and the planet's air shell):
   raise `scene.cycles.volume_step_rate` from 1 to 2-4. This is probably the biggest saving
   on C1; plumes lose a little fine detail.
2. *Fewer samples with adaptive sampling:* C1 128 -> 64, and C2 64 -> 32, with
   `adaptive_threshold` around 0.02-0.03. The denoiser hides most of the difference on dark
   space shots.
3. *Denoise on the GPU:* C1 uses OpenImageDenoise. Switching to OptiX on Windows, or OIDN on
   the GPU, takes the denoise step off the CPU.
4. *Persistent data:* C2 already keeps the scene loaded between frames. C1 doesn't
   (`use_persistent_data`), so it rebuilds the scene on every frame.
5. *Fewer light bounces:* space shots get little bounced light. Max bounces around 4-6 and
   volume bounces 0.
6. *Last resort:* render at 1600x900 and upscale, or render every second frame of slow
   stretches and blend between them. Both are visible; ask the owner first.

**Post-cinematic pictures** (`../yard-stills/`, the Nessa character sheet): Y1 yard
with Nessa's cut-out layered on is in the game. Nessa is Kilo Nine's forewoman, about
52, text only (Castle relays her). The prologue pages P1 to P6 and P8 are
approved and shipped; P3's Colossus repaint shipped 2026-10-20 with its thrust from the six drive bells. P7 (cargo billets) and Y2
(berths fitted) are repainted as a before/after pair and in the game. Not done: a
picture of Nessa's wall of seals, and C5's crowd still draws a dark-coated stand-in for her.

**How renders are made and shown.**
- Compute is tight: no render for a plain fix; one half-size still (`CINE_SCALE=50`) for a look question; only the affected seconds, every 6-12 frames, for motion; full quality only for approved finals.
- Long motion renders go to the Mac render queue. It renders from `origin/main` with ship inputs copied from Drive `builds/<ship>/<commit>/` and checked against the committed manifests, and writes frames to Drive `builds/<shot>-frames/<commit>/`. Windows scales them to 1920x1080 and adds the C5 title.
- Every review set goes to Drive `review/<asset>/<date>_<what>_<machine>/` with `NOTES.md`, then `tools/review_prune.py --asset <asset> --apply`. Renders are shown to the owner in the coordinating chat.
- Shots pin their Second Wind build: C1 (`c1v2_scene.py`), C2 (`c2_bridge_render.py`), C4 and C5 (`c4x_scene.py`) each name it in `SW_PIN` and read its manifest from `pins/`, never `second-wind-states/`'s latest, so a new ship build reaches a shot only when its pin moves.
- Final ship builds: Second Wind `builds/second-wind/80c78dfac/<state>/`, Broadsword `builds/broadsword/df72dd899/`, Shepherd and Hermes `70f1f58eb`, Colossus as in its manifest. All are owner-accepted.

## Asset packages

| Asset / beat | Source and review |
|---|---|
| Dynamic Second Wind | [Per-state assets](second-wind-states/README.md): derelict, patched, refit and restored, one appendable collection each at 0.65 m per unit, with anchors and turn reviews. They supersede the earlier full-surface master (old geometry, 1.5 m per unit); the C1, C5 and entrance-card renders append the patched state. |
| 01 Space approach | [space-approach/](space-approach/README.md); the existing view-dependent finish still limits the camera. |
| 02 Atmospheric entry | [entry-window/](entry-window/README.md). |
| 03 Forest reveal and Atlas lift | [forest-reveal-realism/](forest-reveal-realism/README.md). |
| 04 Fields, agriculture and freighter | [managed-land-realism/](managed-land-realism/README.md). |
| 05 Working industrial port | [port-refinement/](port-refinement/README.md). |
| 06 Damaged landing | [landing-wreck-pass/](landing-wreck-pass/README.md). |
| Style authority | [realism-lookdev/](realism-lookdev/README.md): approved native window and landscape masters. |
| Production guidance | [Cinematic skill](../../../.agents/skills/cinematic-production/SKILL.md) covers style, parallax and geometry-controlled reskinning. |

## Earlier studies and source dependencies

Keep these packages in place: later build scripts and saved scenes refer to them.
Their source models, generated reference art, prompts and stills remain available.
The cleanup retires selected encoded exports, not the underlying technique or art.

| Package | Role / successor |
|---|---|
| [animatic/](animatic/README.md) | Original camera, scale and landing blocking. The complete greybox movie remains as a staging reference. |
| [forest-lookdev/](forest-lookdev/README.md) | Earlier modeled foliage experiment; current environment uses layered realism plates. |
| [painted-window/](painted-window/README.md) | Single-panorama/projection study and source artwork. |
| [parallax-window/](parallax-window/README.md) | Layering and cabin-stability work. The later [Atlas lift movie](parallax-window/review/atlas-lift/jangala-parallax-atlas-lift.mp4) remains as a motion reference. |
| [layered-next-scenes/](layered-next-scenes/README.md) | Shared blocking for the fields, port and landing; later textured shots supersede its encoded previews. |
| [port-approach-realism/](port-approach-realism/README.md) | Initial port geometry/materials used by the refined port. |
| [second-wind-surface-proof/](second-wind-surface-proof/README.md) | Earlier ship materials, physical detail and landing mechanics used by the later landing sources. |
| [landing-refinement/](landing-refinement/README.md) | Opposite-side approach and lift effects; damaged landing supersedes its movie. |
| [sequence-review/](sequence-review/README.md) | Earlier three-shot assembly recipe; the current six-shot cut is under `arrival-sequence/`. |
| [orbital-study/](orbital-study/README.txt) | Rejected orbital docking composition. Reference layers, prompts, compositor and stills remain; it does not describe the current surface landing. |

Current movies and stills are under each package's `review/`; sources and inputs
remain alongside their scripts. Keep new temporary frames in ignored `build/`.
The [cleanup record](../cleanup-record.json) identifies retired exports and their
recovery commit. Old media receipts are historical, and older render/assembly
commands may regenerate those ignored movies before their validators can run.
No source footage needed by the current six-shot assembly was removed.
