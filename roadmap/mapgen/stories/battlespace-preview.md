# Battlespace Preview (pre-battle) — 🔮 SPECULATIVE / not committed

> Forward-looking idea, parked deliberately. Pre-generate the actual
> battlespace before the fight and show it as an *interesting preview* on the
> pre-battle screen — replacing the old decorative planet-crop "map" that
> command-powers S8 dropped. **We don't yet know we want this**; this doc
> exists so the idea isn't lost and so the S8 loadout layout is designed to
> stand on its own without depending on it.

## The idea

When the player opens the pre-battle loadout screen (the canonical
`BriefingScreen`, command-powers S8), run mapgen for *this* mission ahead of
time and present the result as a preview — not a flat decorative crop, but
something that conveys the fight to come: the terrain/biome, compound layout,
LZ candidates, enemy garrison density, objective positions. It turns dead
flavor space into genuine pre-battle intel and a reason to look.

Why it's appealing:
- The pre-battle screen currently has no diegetic "what am I walking into."
- It pairs naturally with **command-powers** decisions — you'd slot powers and
  commit a detachment *against a battlespace you can see*, not blind.
- It's the obvious host for the **drop-geography / LZ-picker** thread
  (command-powers overview §"Landing zones & drop geography", S6): pick your LZ
  *on the previewed map*.
- Survey Equipment's "pre-battle intel" double-life (command-powers overview
  open fork #1) could *gate how much of the preview you see* — bring the right
  ship, see more of the battlespace.

## Why it's a separate story (and parked)

- **Hard dependency on deterministic pre-generation.** The preview is only
  honest if the battle uses the *same* generated map. That means generating at
  briefing time (seed + layout) and replaying it at battle entry — a real
  mapgen/seed-persistence change, not a UI tweak. See
  [`../composable-pipeline.md`](../composable-pipeline.md).
- **Cost vs. unknown value.** Running mapgen pre-battle (or a cheap preview
  approximation) has a cost; we don't yet know the feature earns it. Better to
  ship the S8 loadout layout *without* it and add this only if the want is real.
- **Presentation is open.** Full generated render? A stylized schematic /
  recon-overlay? A cheap "biome + compound-count" summary? Undecided.

## Open questions

- Generate the full battle map pre-battle and persist the seed, or a cheaper
  preview-only approximation that the real gen must then honor?
- How much is *always* shown vs. gated behind recon assets (Survey Equipment /
  Apogee / Hi-Res Sensors — the command-powers recon family)?
- Is the preview interactive (LZ-pick, S6) or read-only intel first?
- Where it sits in the S8 layout once that exists (the dropped-map corner, a
  dedicated panel, a toggle/expand).

## Dependencies / cross-refs

- **mapgen** — deterministic pre-generation + seed persistence is the gating
  capability. ([`../overview.md`](../overview.md),
  [`../composable-pipeline.md`](../composable-pipeline.md))
- **command-powers S8** — the pre-battle loadout screen this would live on; S8
  dropped the decorative map and is built to not need this.
  ([`../../command-powers/stories/s8-pre-battle-loadout-screen.md`](../../command-powers/stories/s8-pre-battle-loadout-screen.md))
- **command-powers S6** — drop geography / LZ-picker; the interactive form of
  this preview.
