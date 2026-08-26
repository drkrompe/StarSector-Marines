# Ship interior story board

Status: ACTIVE — seven bounded stories are open; none has shipped.

Written: 2026-08-26

Updated: 2026-08-26 — added compartment elevation ahead of the facility fills and retargeted the fill acceptance away from reproducing today's rooms.

The first five stories are sequenced and each depends on the one before it.
`deck-capacity-upgrades.md` is gated on an economic owner, and
`boarding-deck-missions.md` is gated on a mission model.

| Story | Status | Outcome |
|---|---|---|
| `ship-deck-family.md` | PLANNED | Add the longitudinal deck family: hull profile, spine, frames, zones, transverse bulkheads, and a deck graph that annotates the shared topology tier. |
| `compartment-elevation.md` | PROPOSED | Give a compartment a non-overlapping raised level so large bays gain articulation, scale, and overlook positions. |
| `facility-room-themes.md` | PLANNED | Fill mech bay and barracks compartments from parameters, keeping today's structural arrangements and replacing their sparse fills. |
| `fixture-derived-ambient-routes.md` | PLANNED | Derive crew routes from placed fixtures so a generated compartment is inhabited rather than empty. |
| `flagship-deck-adoption.md` | PROPOSED | Retire the two constant scene layouts and host Barracks and Mech Lab on generated flagship decks. |
| `deck-capacity-upgrades.md` | PROPOSED | Make facility capacity spatial and let a bounded upgrade transaction change the room. Needs a named economic owner first. |
| `boarding-deck-missions.md` | PARKED | Generate hostile prize decks for boarding once a mission model owns objectives and extraction. |

Historical implementation slices belong in `shipped.md`.
