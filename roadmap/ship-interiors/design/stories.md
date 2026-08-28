# Ship interior story board

Status: ACTIVE — seven bounded stories are open; none has shipped.

Written: 2026-08-26

Updated: 2026-08-27 — the flagship story is now `company-ship-deck-adoption.md`: the company lives on a ship the player chooses out of their fleet, and its direction is `company-ship.md`. The mech bay, berthing, the mess and the firing range are furnished and publish their jobs; a shift still reaches only one compartment.

The first four stories are sequenced and each depends on the one before it.
`deck-capacity-upgrades.md` is gated on an economic owner, and
`boarding-deck-missions.md` is gated on a mission model.

| Story | Status | Outcome |
|---|---|---|
| `ship-deck-family.md` | IN PROGRESS | Deck, rooms and circulation are integrated. Owes the transverse bulkhead chokepoint sequence and the breach point. |
| `facility-room-themes.md` | IN PROGRESS | Bay, berthing, mess and range are fitted. Owes the density and empty-region seed sweeps, and a refit level that changes what a floor holds. |
| `fixture-derived-ambient-routes.md` | IN PROGRESS | Shifts span compartments, every job a marine has is published by a generated room, and live fire resolves on a generated range. Owes the seed sweep. Model now lives in `ai-nouns.md`. |
| `company-ship-deck-adoption.md` | PROPOSED | Retire the two constant scene layouts and host Barracks and Mech Lab on the company ship's generated deck. |
| `company-ship-selection.md` | PROPOSED | Let the player choose the company ship at founding and transfer to another hull later. Direction is in `company-ship.md`. |
| `deck-capacity-upgrades.md` | PROPOSED | Make facility capacity spatial and let a bounded upgrade transaction change the room. Needs a named economic owner first. |
| `boarding-deck-missions.md` | PARKED | Generate hostile prize decks for boarding once a mission model owns objectives and extraction. |

Historical implementation slices belong in `shipped.md`.
