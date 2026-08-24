# Backlog

This file holds uncontracted future direction only. Concrete work belongs in
the owning feature's `design/stories.md`; shipped work belongs in its noun model,
ledger, code, tests, and Git.

## Audio and assets

- **Music lead** — evaluate the
  [Eternity metal sci-fi pack](https://davidkbd.itch.io/eternity-metal-scfi-music-pack)
  when the battle music direction is contracted.
- **Real asset import end to end** — process a representative FBX or glTF asset,
  load the resulting `.mlmodel`, and render it through a production drawable.
- **Skinned animation runtime** — connect the existing animation, skeleton, and
  retargeting substrate to a skinned drawable when a representative asset exists.

## Campaign and progression

- **Personnel scale** — replace fixed roster limits with a progression-owned
  capacity model once company growth has a concrete campaign pressure.
- **Deniable contracts** — high reputation with a faction may unlock covert work
  against its enemies as a mission category, not a parallel client hierarchy.
- **Post-operation summary** — give casualties, career evidence, recovered
  materiel, and campaign consequences one readable surface without moving their
  authority out of personnel, loot, progression, or contracts.

## UI and presentation

- **Interactive retained scrollbar** — extend the visual scroll thumb with
  track and drag interaction when a production surface needs direct navigation.
- **Captain roster environment** — explore a 3D bridge or equivalent company
  space as a presentation over the persistent roster, not a second roster model.
- **Legacy bitmap-font wrapping** — add bounded variable-height wrapping for
  immediate-mode surfaces that cannot yet use retained text layout.
- **Tooltip primitive** — generalize the existing mission-popup shape only when
  a second retained consumer establishes the shared interaction contract.
- **Faction context accents** — let selected-client surfaces borrow faction
  colors without making color the only carrier of identity or state.
- **Political mission map** — derive holdings and contested regions from campaign
  facts. Until that substrate exists, the map remains a non-load-bearing preview
  and cards retain every decision-critical fact.

## World and visual polish

- **Planet pole treatment** — fade or tint near the poles to hide
  equirectangular texture distortion.
- **Texture-aware mission placement** — reject unsuitable water or ice positions
  when planet-texture sampling becomes a stable campaign service.

## Architecture candidates

- **Shared combat FX and sound presentation** — extract a frame-parameterized
  presenter only after standalone and vanilla-host weapon dispatch demonstrably
  diverge.
- **Per-panel legacy widget roots** — isolate rebuilds where an immediate-mode
  screen still loses unrelated hover or input state.
- **Renderer naming** — rename `BridgeRenderer` after another real consumer proves
  that its framebuffer scene role is no longer bridge-specific.
- **Planet intel row geometry** — give the repeated name-row calculation one
  owner when that legacy surface is next touched.
- **Variable-height legacy labels** — pair `LabelWidget` sizing with legacy
  bitmap-font wrapping rather than building another text-layout path.
- **Mission-name internationalization** — move hardcoded generation templates
  behind the existing strings authority with explicit formatting inputs.
- **District-theme convergence** — reconcile `DistrictTheme` and
  `MapDistrictTheme` as one semantic model when their next consumer requires it.

## Cleanup candidates to promote

- **Bridge debug quadrants** — retire the remaining false quadrant scaffolding
  from `BridgeRenderer` with a bounded render smoke check.
- **Neutral battle timestep** — move the shared fixed-tick constant out of
  `BattleSimulation` so narrow battle contracts do not import the coordinator
  solely for a literal.
- **Squad leadership order invariant** — decide whether roster order or leader
  identity is authoritative, then make undermanned leadership deterministic.

These candidates should move to their owning feature boards before
implementation; they are listed here only until their story contracts exist.

## Translation and community

- **i18n coverage audit** — periodically sweep user-facing copy for hardcoded
  English outside the strings authority.
- **Translation template** — eventually ship a commented strings template that
  explains the supported override pattern.
