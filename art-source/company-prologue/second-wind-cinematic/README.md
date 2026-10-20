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
| C2 bridge | 15 s | `patrol-abeam/` (`c2-layout.md`, `c2_bridge_render.py`) | Camera set approved (walk, duck, breath, frame, lean, lens). Final Second Wind and Broadswords wired. Glass reflection kept faint, bridge lit brighter. Castle passes once at 5.5-6 s. |
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
4. Packaging: the in-game C5 composer still builds an 8 s shot and must take the 40 s one; the new voice files need `processAudio`; then re-export every shot (`cinematic_export.py`) and run `ArrivalCinematicStringsTest`.

**Post-cinematic pictures** (`../yard-stills/`, the Nessa character sheet): Y1 yard
with Nessa's cut-out layered on is in the game. Nessa is Kilo Nine's forewoman, about
52, text only (Castle relays her). The prologue pages P1, P2, P4, P5, P6 and P8 are
approved and shipped; P3 (Colossus repaint) awaits the owner, and P7 (cargo billets)
still needs a new background.

**How renders are made and shown.**
- Compute is tight: no render for a plain fix; one half-size still (`CINE_SCALE=50`) for a look question; only the affected seconds, every 6-12 frames, for motion; full quality only for approved finals.
- Long motion renders go to the Mac render queue. It renders from `origin/main` with ship inputs copied from Drive `builds/<ship>/<commit>/` and checked against the committed manifests, and writes frames to Drive `builds/<shot>-frames/<commit>/`. Windows scales them to 1920x1080 and adds the C5 title.
- Every review set goes to Drive `review/<asset>/<date>_<what>_<machine>/` with `NOTES.md`, then `tools/review_prune.py --asset <asset> --apply`. Renders are shown to the owner in the coordinating chat.
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
