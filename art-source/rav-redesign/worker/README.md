# Skitterer (nest worker) bone art

The skitterer is the Rav Bloom's non-combat nest worker (`colony-breach-nouns.md`, Swarm strain: Skitterer). It
is drawn on the Runner's skeleton by `RavRunnerComposer` with its own bone set in
`mod/graphics/battle/rav-worker/`: the Runner's file names and registration, with no arm sprites. Its pincer
mandibles are part of `fore.png`. The game draws it at about 0.68 of a Runner.

Three look options were painted, all geometry-first (`geometry-first-imagegen.md`) on the Runner body layer's
1024x1536 canvas and rows (head 333, neck 420, shoulder 541, hip 958). The Runner's
`exploration/rounds/round-6/layers/body-slim-b.png` was the material reference.

| Option | Body plan |
| --- | --- |
| `hauler` (shipped) | Squat, with a broad flat back. A shallow cradle of ivory ribs runs along the spine, with one amber node and short hooked pincers. |
| `tender` | A narrow plated front and a swollen translucent amber-lilac gut sac with seed masses, plus feeler palps. |
| `mite` | A round, pale whelp-lilac tick carapace with crimson seams. The head is tucked under an ivory shield lip and has short hooked pincers. |

## Files

- `make_guides.py`: draws `guides/<option>-guide.png`, the flat colour-blocked silhouette of each option. It also
  draws `guides/<option>-v2-ref.png`, the v1 raw with its mandibles cleared and redrawn as toothed hooks (hauler
  and mite only), which serves as the finish pass's geometry.
- `prompts/<option>-body.prompt.txt` (v1, over the guide) and `prompts/<option>-body-v2.prompt.txt` (the finish
  pass in the Runner's faceted chitin, over the v2 ref; the tender used its v1 raw).
- `raw/options/<option>-body-v1.png` and `-v2.png`, each with its `.provenance.json`. The exporter cuts the v2
  raws.
- `export_worker_rig.py --option <hauler|tender|mite> [--out <dir>]` writes one option's bone set into
  `mod/graphics/battle/rav-worker/`, overwriting it. It cuts the body exactly as `../export_runner_rig.py` cuts the
  Runner's (the trunk fades over the neck, splits at mid-spine, and the head is cropped above the neck, at the same
  0.2 scale), so `TRUNK_HALF_PX` and `FORE_PX` are unchanged. The legs are the Runner's shipped leg sprites,
  narrowed across and recoloured for each option.
- `preview_worker.py <out.png> [--set label=<dir> ...]` is a Python port of `RavRunnerComposer` at the rig's
  standing pose and one mid-stride pose. It draws a Runner beside each set at true relative size (0.68x).

## Regenerate

```
python art-source/rav-redesign/worker/make_guides.py
python tools/codex_imagegen.py --prompt art-source/rav-redesign/worker/prompts/<option>-body-v2.prompt.txt \
  --out art-source/rav-redesign/worker/raw/options/<option>-body-v2.png --replace \
  --ref art-source/rav-redesign/worker/guides/<option>-v2-ref.png="Geometry authority: ..." \
  --ref art-source/rav-redesign/exploration/rounds/round-6/layers/body-slim-b.png="Style and material authority; not layout"
python art-source/rav-redesign/worker/export_worker_rig.py --option hauler
```

## Carried loads

The skitterer carries one load in its mandibles, drawn at the tip of its head: `load-feed.png`, a glistening
amber biomass glob in crimson sinew and a lilac membrane, carried from its bore to the brood spire; and
`load-pulp.png`, a ragged wad of chewed bark, splinters and dead leaves bound in lilac creep veins, stripped from a
creep-killed tree. Both are 64x64 with the object filling about 80%, and are drawn about 0.18 cells across, so
they rely on silhouette and value: the feed is warm, round and glossy, the pulp brown, matte and spiky.

- `export_loads.py --guide` draws `guides/loads-guide.png`, both objects colour-blocked side by side on a flat
  chroma-green ground (geometry authority).
- `prompts/loads.prompt.txt` and `raw/loads/loads-v1.png` (with provenance): one generation painting both, with
  `raw/options/hauler-body-v2.png` as the palette and lighting reference.
- `export_loads.py [--preview <png>]` keys out the green (with despill), splits the sheet in halves, crops each to
  its object, pads it square and downsizes it premultiplied into `mod/graphics/battle/rav-worker/`. `--preview`
  draws both at 1x and 4x on a dark violet ground.

```
python art-source/rav-redesign/worker/export_loads.py --guide
python tools/codex_imagegen.py --prompt art-source/rav-redesign/worker/prompts/loads.prompt.txt \
  --out art-source/rav-redesign/worker/raw/loads/loads-v1.png --replace \
  --ref art-source/rav-redesign/worker/guides/loads-guide.png="Geometry authority: ..." \
  --ref art-source/rav-redesign/worker/raw/options/hauler-body-v2.png="Style, palette and lighting authority only; not layout"
python art-source/rav-redesign/worker/export_loads.py
```
