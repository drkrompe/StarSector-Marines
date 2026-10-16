# Second Wind arrival cinematic

The current sequence is **space -> entry -> forest -> fields -> port -> landing**.
Start with the [28-second sequence review](arrival-sequence/README.md) and its
[movie](arrival-sequence/review/jangala-space-to-berth.mp4). These are short silent
editorial excerpts, not a continuous 28-second physical descent or runtime
integration. The [storyboard](storyboard-v2.txt) retains the longer story and voice
planning; later shot notes document accepted camera changes.

## Current work

| Asset / beat | Source and review |
|---|---|
| Dynamic Second Wind | [Full-surface master](second-wind-full-surface/README.md): the reusable damaged ship and full-turn review. It has not yet replaced the ship in existing shots. |
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
