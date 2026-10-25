# Rav Bloom redesign

Sources for redesigning the Colony Breach swarm bodies (Runner, Brute, Spitter;
Matriarch queued). Lore: `swarm-lore-guide.md`; model: `colony-breach-nouns.md`.

## exploration/ (direction pass, 2026-10-21)

Three candidate directions, A FRUITING, B RED TIDE and C SEEDCASE, built as
procedural top-down rigs so their limb timing could be judged before any art was
committed.

- `rig.py`: a small 2D rig renderer. It draws tapered segment chains with
  per-joint lag, a strike curve (wind-up, lash, overshoot, hold, damped
  recovery), flat three-tone shading and a soft contact shadow, all supersampled.
- `creatures.py`: the three directions' Runner, Brute, Spitter and Matriarch,
  with their palettes.
- `guides.py`: draws each direction's concept-plate guide, saved in `guides/`.
- `prompts/plate-*.prompt.txt` and `raw/plate-*.png`: the ImageGen plates made
  over those guides, with provenance. Made with `tools/codex_imagegen.py`.
- `motion.py` and `boards.py`: write the review set (critique, boards, motion
  GIFs, battle-zoom comparison, silhouettes). They take a colony-breach close-up
  render as the ground. Regenerate that render with
  `gradlew commanderEvidence -Pmission=colony-breach -PmaxTicks=600 -PsnapshotEveryTicks=150 -PsnapshotFocus=199,158,112 -PsnapshotWidth=1920 -PsnapshotHeight=1080 -PevidenceDir=build/reports/rav-zoom`.
  The review set is output and stays on Drive, under
  `review/rav-redesign/`.

The rigs stand in for motion and silhouette only. Production art follows
`geometry-first-imagegen.md` once the owner picks a direction.

## Spitter (design settled 2026-10-24)

Five concept rounds. Review sets are on Drive under `review/rav-spitter/`; the sources are
`exploration/rounds/spitter-1..5/` (rounds 1 to 3 drawn by `exploration/spitter_round.py`).
Rounds 1 to 3 explored legged firers (a domed vent or mortar mass on the back, legs under the
body), after which the owner parked the strain. Round 4 tried bipeds with a dome; the owner
rejected all of them and asked for something "more hydralisk like".

Round 5 settled the body: **the cobra hood in indigo-black**, a "vertical" creature, an upright
serpent seen from above. The owner then reworked it into a hydralisk knockoff:
- no arms or claws;
- the body rears out of a thick tail that lies curled round its base when it stands or fires, and
  stays planted while only the body and head rear and lunge; its root bends to stay joined to the body;
- on the move the tail uncurls to the same length and slithers to show direction; there is no turret,
  the tail follows the facing;
- the head is **Ridge** (head round 4, `exploration/rounds/spitter-head-1..4/`): one ivory crest line
  from the snout down the spine and tail, two slim crest blades;
- to spit, the head rears back with the crest flared and the lower jaw thrust forward, so the camera
  above sees the mouth only as a gap at the front, never on top of the skull;
- dead, it bursts as a Brute does (`RavBurst`): the head comes away, one or two lengths of tail are
  thrown, over the Runner's red blood.

The acid stays a dark venom green.

It is built from separate parts, the way infantry are. ImageGen copies guide shapes literally, so every
guide is a top-down render of a 3D swept-sphere pose model (`exploration/spitter_pose_model.py`, which
also renders side and three-quarter views to check the pose). Production sources are in `spitter/`:
- `guides/ridge-part-*.png` are the pose model's renders of each part alone, in one shared frame;
- `raw/ridge-body.png`, `ridge-head.png`, `ridge-head-spit.png` and `ridge-tail.png` were each
  generated over their guide, with head round 4's `p2-ridge.png` as the design reference
  (`ridge-head-spit-v1.png` is the rejected first spit head, mouth on top);
- `raw/acid.png` is the acid sheet: the glob, two splashes and a pool.

Each has its prompt and provenance. `export_spitter_rig.py` exports the parts into
`mod/graphics/battle/rav-spitter/`, cuts the straight tail into short slices, and appends the
splashes and the pool to the decal strip. `RavSpitterRig` and `RavSpitterComposer` animate the
parts, and the `rav-spitter-motion` snapshot suite shows the glide, the lob and the death.

## Matriarch (design settled 2026-10-21)

The mother-bore heavy strain. The owner's brief was "an Ultralisk-like creature": a boss-scale plated
quadruped with scything blades, which wades through fortifications. Sources are under
`exploration/matriarch/round-1..3/`; `exploration/matriarch_sheet.py` builds the play-zoom sheets.

The owner's calls, in order:
- **Round 1, body plan: #4 Siege Crown.** The Brute's mace head grown into a vast fan-shaped crown of
  bone spikes, wider than the shoulders. Two curved scythes come out from under the crown's rim, beside
  the jaws. The legs stand under the body. Rejected in that round: Scythe Mother, Four Blades, Brood-Back,
  Tusk-Reaper, One-Armed Ancient, Shellback and Spine-Queen.
- **Palette:** "a slightly different color palette" from the Runner's lilac and the Brute's plum.
  - Round 2 picked #3, black with crimson sinew.
  - **Round 3 settled #5, Charcoal and Dried Blood:** matte charcoal-black plates, dried-blood red on
    worn edges, deep wine-red sinew, a yellowed ivory crown and scythes, small amber eyes.
- **Size proposal:** 3.9x the Runner's length, about 4.5 cells nose to tail and 2.5 cells across.
  The owner has not confirmed it.

**Motion:** `exploration/motion_matriarch.py` runs over `matriarch_skeleton.py`. The layers are in
`exploration/motion-matriarch/`, with `hero.png` = round 3 #5.

## Harrier wingbeat (exploration/flier/wingbeat/, 2026-10-23)

The lilac glider's frame-by-frame wingbeat (seven frames after the glide) and
its two fold-into-dive frames. `make_guide.py` poses the glide drawing's wing
skeleton in 3D for each frame and draws it as flat shaded geometry under the
glide's own body pixels, nine frames on one 3x3 sheet (`guide-all.png`).
`raw/wingbeat.png` is the ImageGen reskin of that guide (`wingbeat.brief.txt`,
the round-1 sheet as identity reference), with provenance. `export_harrier.py`
cuts each frame from its guide cell, keeps only the generated wings and lays the
glide body back over them, so the body never changes from frame to frame.

The second sheet, `guide-dive.png` / `raw/dive.png` (`dive.brief.txt`), carries
the dive with its wings still showing and the two frames sweeping into it, four
tumbling fall frames, and two corpses that the exporter grades duller. The
first sheet's own fold frames (`r1-fold-*`) are no longer exported.

The exporter also cuts the glide body into segments (`head`, `abdomen`,
`tail-1..3`; the live frames keep only the thorax) at the joints in `JOINTS`,
and prints each joint's offset for `HarrierLook`'s joint constants.
