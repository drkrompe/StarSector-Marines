# Bridge Host Live Acceptance

Status: READY FOR ACCEPTANCE

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before running this story.

## Shipped substrate

The bridge draws live units and fog, advances roof and unit visibility fades,
plays camera-projected combat audio and effects, and hides the vanilla
ship-info widget. These paths are code-complete but several host-specific
presentation claims have not had a final live verdict together.

## Acceptance pass

- Infantry, turrets, hubs, dead poses, and health feedback read at the current
  combat-world scale without double-rendering invisible proxies.
- Fog agrees with unit visibility; roofs and units fade in both directions as
  vision changes.
- Rifle, impact, explosion, and death audio follows the projected ground scene
  without fighting vanilla fleet audio.
- The ship-info panel and switch-ship prompt are absent or any surviving prompt
  is recorded as a bounded HUD-policy issue.
- Proxy footprint, world scale, and damage scale make structures targetable and
  durable enough to read over several fighter passes.

## Completion rule

Record measured discrepancies as narrow implementation stories. If the pass is
clean, fold any lasting tuning law into `vanilla-combat-bridge-nouns.md`, add the
ledger row, and delete this story.
