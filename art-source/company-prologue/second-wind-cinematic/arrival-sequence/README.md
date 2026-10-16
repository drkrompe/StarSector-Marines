# Space to Jangala's surface berth

This review adds the two requested opening beats to the existing approach and damaged landing: space, entry, forest and fields, working port, then the berth. It preserves the earlier individual shots. All source and output files stay under art-source; campaign travel, skip behavior, dialogue and runtime playback are unchanged.

| Review time | Shot | Purpose |
|---|---|---|
| 0–4 s | Space to Jangala | The accepted damaged ship drifts bow-first toward a much larger lush planet. |
| 4–8 s | Entry through the crew window | Brief exterior heating filaments fade into cloud, then daylight. |
| 8–12 s | Forest reveal | The familiar sealed viewport reveals the native canopy and Atlas lift. |
| 12–16 s | Managed fields | Agriculture, moving sprayer and freighter establish settlement. |
| 16–20 s | Working port | Freight traffic and the industrial district pass abeam. |
| 20–28 s | Damaged landing | The ship recovers from uneven descent and transfers its weight to the cradle. |

These are compact review excerpts with editorial ellipses, not twenty-eight seconds of continuous physical descent. The entry endpoint matches the forest opening; its single shared 1/24-second sample keeps the handoff legible. The earlier storyboard's longer timing remains dependent on eventual narration and pauses. No new narration is generated here.

The source authority is the eight-page campaign intro and active prologue strings, together with the existing storyboard's destination, pressure-integrity and inspection ordering. Nessa is already selected and contacted before entry. The ship arrives with civilian passengers and the separate escort section; the berthing conversion and Engineering work remain ahead. The later accepted starboard landing camera supersedes the storyboard's original same-side exterior suggestion. This package does not edit the unrelated in-progress storyboard copy on main.

The installed vanilla descriptions for planet_jangala support the lush biosphere, farms, seas and large weather systems; station_jangala describes an orbital station. Our visible coastline and surface yard are authored geography. The new space plate is a guided illustration of this world, not a recovered canonical surface map. Exact ImageGen prompt, reference roles, dimensions and hashes remain in the space-approach package.

## Reproduce and review

Render and encode the two new sibling packages before running `assemble_sequence.py` with Python and FFmpeg/FFprobe on PATH. The assembler checks the six source movies, joins them with hard editorial cuts, encodes H.264 CRF18 yuv420p at 1920×1080/24 fps, and fully decodes the result. It writes `review/jangala-space-to-berth.mp4` and a hash-bound media report. No audio is included.

Source manifests and geometry/projection limits stay with each shot:
[space approach](../space-approach/README.md),
[entry window](../entry-window/README.md),
[forest](../forest-reveal-realism/README.md),
[fields](../managed-land-realism/README.md),
[port](../port-refinement/README.md), and
[damaged landing](../landing-wreck-pass/README.md).

The space shot uses an actual moving ship mesh in front of a fixed distant planet plate. Its limited aspect preserves the accepted projection; it is not a rotating complete texture atlas. Entry light and clouds are authored layered effects, not plasma or fluid simulation. The complete cut is a textured motion review, with final timing, audio and runtime integration still separate work. The 28-second movie is 13.83 MB, contains 672 frames, and passed strict full decode. Browser playback completed at 1x with no reported dropped frames; sampled visual observations and limits are retained in `review/motion-review.json`. The first entry render records after-render hashes only; its report distinguishes that from the in-process hash binding added for future renders.
