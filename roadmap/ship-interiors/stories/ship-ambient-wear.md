# Ambient ship wear and lighting

Status: IMPLEMENTED -- construction evidence ready; live visual acceptance open.
Written: 2026-10-12
Updated: 2026-10-12 -- persistent wear and condition-driven deck lighting.

## Scope

Make Second Wind read as a used vessel under restoration. Sparse scuffs, old
welded plates, paint loss and scorch scars persist across room repairs. Working
compartments carry steady lights; damaged workshop/bay rooms use amber emergency
fixtures with occasional local dips. Repairs return those fixtures to steady
light without moving rooms or erasing the hull's history.

The model belongs to `ship-interiors-nouns.md`. Existing restoration orders own
repair state and costs; this story adds no separate purchase or combat effect.

## Acceptance

- [x] Wear is confined to deck floors and does not change navigation, capacity,
  fixture placement or the furnishing random stream.
- [x] Steady and faulty lighting share the ordinary live/headless renderer and
  company deck clock. Room condition controls fault recovery.
- [x] Ship, room and animation evidence covers emergency and repaired lighting.
- [ ] In-game review at supported UI scales: readable crew/equipment, unobtrusive
  flicker, smooth page changes and acceptable frame time on large company hulls.

Local lighting is a presentation layer. No atmosphere, electrical simulation,
combat visibility rule, new restoration bill or generic D-mod interpretation
is introduced. Wider room restoration chains need their own concrete benefits.
