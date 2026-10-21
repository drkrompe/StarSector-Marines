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
