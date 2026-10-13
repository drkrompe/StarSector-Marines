# Ship interior story board

Status: ACTIVE — seven bounded stories are open; two have shipped.

Written: 2026-08-26

Updated: 2026-10-12 -- ambient wear and condition-driven lighting implemented; live visual acceptance remains.

The first two stories are sequenced and the second depends on the first.
`deck-capacity-upgrades.md` is gated on an economic owner, and
`boarding-deck-missions.md` is gated on a mission model.

| Story | Status | Outcome |
|---|---|---|
| `ship-deck-family.md` | IN PROGRESS | Deck, rooms and circulation are integrated, and circulation is looped rather than left a tree. Owes the transverse bulkhead chokepoint sequence and the breach point. Its link measurement leaves a question for the packing: a third of the hull is empty aft while the bow is packed too tight for any corridor to be added. |
| `facility-room-themes.md` | IN PROGRESS | Every shipboard purpose now has its own fitting; nothing ships on the generic aisle treatment any more. The fill defect the story existed to name is fixed at the source, and the follow-on defect it exposed is written up as a law: a line of fixtures across a room is a wall unless it is told not to be, which walled a galley and a sick bay off from their own ships without either of them looking wrong. Still owes the empty-region instrument that can tell an argued aisle from an abandoned middle. |
| `room-authoring.md` | IN PROGRESS | Make a shipboard room an authored artifact the workbench edits: footprint, floors and walls, and the fixtures on them, seeded from the procedural fitting and compared against it through the battle renderer before it is accepted. Rests on the fact that a room's footprint is fixed and only its pose varies, so the placer is not involved. |
| `company-ship-selection.md` | IN PROGRESS | Founding, transfer, the refit fee and the loss toll all land: the player picks the company ship, pays the yard to move it, and is displaced - and cut down - when it does not come home. Left: whether named officers are at risk with her. |
| `deck-capacity-upgrades.md` | PROPOSED | Make facility capacity spatial and let a bounded upgrade transaction change the room. Needs a named economic owner first. |
| `ship-ambient-wear.md` | IMPLEMENTED | Persistent restoration-hull scars, steady room lights and local emergency-light faults; live visual/performance acceptance remains. |
| `boarding-deck-missions.md` | PARKED | Generate hostile prize decks for boarding once a mission model owns objectives and extraction. |

`cerberus-company-start.md` implements the optional permanent frigate conversion and
compact conversion room program; its company-view story owns live acceptance.
Second Wind's original hull reserves a larger four-berth bay within its existing
envelope; `ship-condition-survey.md` owns the Hound recovery and paid berth repairs.

`machine-shop-restoration.md` owns the first staged facility restoration in the
company-view board: clearance, machinery repair and installed tooling. Its spaces
remain fixed; wider capacity reallocation stays with `deck-capacity-upgrades.md`.

Historical implementation slices belong in `shipped.md`.
