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

## Where it stands (2026-10-22)

The shipped cut is **C1 -> C2 -> entry -> C3 -> C4 -> C5** (about 2:17 with the
title). Every shot's content is owner-approved and its final render is packaged into the game;
what remains is the final mix.

| Shot | Length | Source | State |
|---|---|---|---|
| C1 approach | 37.7 s | `corvus-approach/` (`c1v2_scene.py`, `c1-v2-shot.md`) | All ships on final builds; half-size motion check approved. Planet lock and captions are runtime data (`cinematic_export.py --shot c1`). |
| C2 bridge | 15 s | `patrol-abeam/` (`c2-layout.md`, `c2_bridge_render.py`) | Camera set approved (walk, duck, breath, frame, lean, lens). Final Second Wind and Broadswords wired. Glass reflection kept faint, bridge lit brighter. Castle passes once at 5.5-6 s. Bulkhead detail round the slits (`c2_bulkhead.py`, owner-approved round 2) is on by default; `--no-bulkhead` is the control. Renders the Second Wind build pinned in `c2_bridge_render.SW_PIN` (`pins/`); a new build means moving the pin. |
| Entry | 7 s | `patrol-abeam/compose_entry.py` | Approved; a re-entry plasma pass (sheath, shock front, embers, glare in the effects kit's colours) is in owner review (2026-10-22). |
| C3 window | 16 s | `first-open-sky/` | Approved; a look pass (graded onto C4/C5's jungle, a freight checkpoint town, its road and a relay mast on the far bank) is in owner review (2026-10-22). |
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
1. Final mix, shot by shot, from the owner's settings. Every level is data in
   `arrival-audio/arrival-mix.json`: each cue's `gain_db` (and fades, and `duck_db` for music),
   and the music cues (strings over C1-C4 and Ending A over C5, on the score desk's dB scale,
   ducked 8 dB under every caption). Change a number, then
   `python art-source/company-prologue/second-wind-cinematic/cinematic_export.py --timeline-only --preview`
   (`--shot c5` for one shot) rewrites the timelines and writes
   `build/cinematic/preview/arrival.mp4` to hear it, in seconds and without re-encoding video.
   Every effect on `sfx-cues.md` is in the game: `arrival-audio/build_arrival_stems.py` renders
   each layer of `sfx_mix.SHOTS` (chosen take or synth stem, offset, filter, loop, fades and dB
   envelope) to one clip, `jangala_sfx_<shot>_<layer>`, played as a one-shot at the sheet's time
   with the sheet's level as its starting `gain_db`. Re-run it, then `processAudio` and
   `--timeline-only`, after changing the sheet itself.
2. An in-game play-through of the packaged cut (`runStarsector`).

**Packaging.** All six finals are in the game (2026-10-22). `cinematic_export.py --from-drive`
copies each shot's approved frames off Drive (`FINALS`: C1 and C4 on their plumes-trim builds,
C5 titled) into `build/cinematic/` and encodes them. C1's frames 300-904 are then replaced by
its planet fix: the final rendered Jangala's air shell unscaled, an opaque grey ball, and
`corvus-approach/planet_fix.py` composites the corrected planet into the shipped frames from two
planet-only layers (`c1v2_scene.py --planet-layer`, with and without `--air-legacy`). C1's planet lock is read from C1 v2's own
camera (`corvus-approach/c1v2_planet_track.py`, run in Blender before exporting C1) and lands
on Approach's customs call (hail 03), the first transmission with Jangala wholly in frame.

## Final renders (render coordinator, 2026-10-20)

Every render goes through the shared render queue (`render-queue` skill,
`python tools/render_queue.py status`). The render coordinator chat owns the queue and gives the
go-ahead (owner, 2026-10-20); nobody starts Blender by hand. Gates are open: renders run day and
night, and the owner can ask for a pause at any time. A final rendered in chunks resumes where it
stopped, so a pause costs at most the frame in progress: C1 skips frames it already has, and C4/C5
do the same under `CINE_RESUME=1` (without it they clear their frames folder and start clean).

**Second Wind is the two-Vulcan build `dc62710dc`** everywhere her guns show (owner,
2026-10-20); each shot pins it with `SW_PIN` and `pins/second-wind-dc62710dc-patched.manifest.json`
and refuses any other .blend. Everything rendered on `80c78dfac` before that is superseded.

| Shot | State |
|---|---|
| C1 | Done: all 905 frames with `--profile cheap` (64 samples, adaptive 0.02, 6 bounces, GPU denoise), chosen from a measured A/B (review `review/cinematic-c1/2026-10-20_settings-ab_windows/`): about half the time, no visible loss. 18 queue chunks `c1-final-cheap-e0c15dc08-*`, either machine, to Drive `builds/c1-final-dc62710dc-e0c15dc08/frames/`. |
| C2 | Done: `c2-final-dc62710dc-71c4b051b` (bulkhead detail on, 64 samples), frames and `c2-bridge.mp4` in Drive `builds/c2-final-dc62710dc-71c4b051b/`. |
| C4 | Rendering: content final at `f2323ac8f` (port roads, yard vehicles, effects-kit drive plumes), `CINE_FINAL=1` (64 EEVEE samples, full shadows; owner 2026-10-21 from a layout/final A/B, about 34 s a frame), 60-frame chunks `c4-final-dc62710dc-f2323ac8f-*` on the Mac, then a movie job, to Drive `builds/c4-final-dc62710dc-f2323ac8f/`. |
| C5 | Rendering: same content and settings (about 50 s a frame), 60-frame chunks `c5-final-dc62710dc-f2323ac8f-*` on Windows, then the title job (`c5_title.py`), to Drive `builds/c5-final-dc62710dc-f2323ac8f/`. Nessa keeps the stand-in (`C5_NESSA` off). |
| Entry, C3 | Need C3's layer build first. |

Faster settings not yet used: biased volume stepping (`--volume-step-rate`) is about 8x faster
on C1's late frames but changes the approved look (harder terminator band, brighter planet), so
it needs a look sign-off first.

**Post-cinematic pictures** (`../yard-stills/`, the Nessa character sheet): Y1 yard
with Nessa's cut-out layered on is in the game. Nessa is Kilo Nine's forewoman, about
52, text only (Castle relays her). The prologue pages P1 to P6 and P8 are
approved and shipped; P3's Colossus repaint shipped 2026-10-20 with its thrust from the six drive bells. P4's turrets were repainted to build dc62710dc (two Vulcans, the other mounts capped) and shipped 2026-10-20; P5 shows no mount. P7 (cargo billets) and Y2
(berths fitted) are repainted as a before/after pair and in the game. Not done: a
picture of Nessa's wall of seals, and C5's crowd still draws a dark-coated stand-in for her.

**How renders are made and shown.**
- Compute is tight: no render for a plain fix; one half-size still (`CINE_SCALE=50`) for a look question; only the affected seconds, every 6-12 frames, for motion; full quality only for approved finals.
- Long motion renders go to the Mac render queue. It renders from `origin/main` with ship inputs copied from Drive `builds/<ship>/<commit>/` and checked against the committed manifests, and writes frames to Drive `builds/<shot>-frames/<commit>/`. Windows scales them to 1920x1080 and adds the C5 title.
- Every review set goes to Drive `review/<asset>/<date>_<what>_<machine>/` with `NOTES.md`, then `tools/review_prune.py --asset <asset> --apply`. Renders are shown to the owner in the coordinating chat.
- Shots pin their Second Wind build: C1 (`c1v2_scene.py`), C2 (`c2_bridge_render.py`), C4 and C5 (`c4x_scene.py`) each name it in `SW_PIN` and read its manifest from `pins/`, never `second-wind-states/`'s latest, so a new ship build reaches a shot only when its pin moves.
- Final ship builds: Second Wind `builds/second-wind/dc62710dc/<state>/` (owner, 2026-10-20: the game start's two Vulcans on WS 001/002, WS 003/004 empty; it replaces 80c78dfac, so C1 frames 0-649 and C5 re-render, and C2 and C4 render on it). C1, C2 and C4 refuse a Second Wind .blend whose sha256 is not their pin's. Broadsword `builds/broadsword/df72dd899/`, Shepherd and Hermes `70f1f58eb`, Colossus as in its manifest. All are owner-accepted.

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
