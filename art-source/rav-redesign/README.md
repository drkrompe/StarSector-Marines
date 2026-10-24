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

## Spitter (PARKED 2026-10-21)

The owner parked the Spitter ("thinking about dropping the spitters for now") after three
concept rounds. Review sets are on Drive under `review/rav-spitter/`. The sources here are
`exploration/rounds/spitter-1..3/`, made with `exploration/spitter_round.py`:
- the default mode draws the concept sheet;
- `--fire` adds the side views caught firing;
- `--pack` puts two Spitters behind five Runners.

What the owner settled before parking:
- **Acid:** a dark venom green, darker than round 1 #6's lime.
- **Body:** symmetric, never lopsided, on two or three legs (round 2).
- **Firing:** must be legible. Round 2 and 3 plates carry a side view of the lob.
- **Palette:** its own, not the Runner's lilac. Round 2's lilac read as "too visually and palette close to the
  swarmer".
- **Read from above:** what separates a firer from a Runner is a round mass on the back, a mortar tube or a domed
  vent.
- **Legs** sit under the body like a hound's or a pig's, swinging forward and back, never sprawled like an
  alligator's. That rule came after round 3, whose splayed tripods and toad break it.

Round 3's strongest reads were #4 indigo, #6 inside-out magenta and #2 bone-white. The owner had not picked one.
To resume, start from round 3, put the legs under the body, and widen the stance only for the brace when it fires.

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
