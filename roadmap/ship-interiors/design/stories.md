# Ship interior story board

Status: ACTIVE — five bounded stories are open; two have shipped.

Written: 2026-08-26

Updated: 2026-08-28 — every shipboard purpose now has a fitting written for it rather than a generic aisle: the mess is a galley behind a servery, the three holds differ from one another, the armoury is a counter with a workshop behind it, the sick bay is a ward plus a clinic, and the heads are ranked rather than represented by a crate. The bridge moved to a purpose of its own, because sharing `CONTROL_ROOM` with a factory booth and a fortress guard post was putting a nav plot in both. Earlier: every furnished compartment publishes work somebody's trade owns, and the ship carries a lounge and a gymnasium so that doing nothing has somewhere to happen; a manned transport and capital both measure at a zero idle share. Earlier: circulation is no longer a tree; a link pass joins dead ends that are near in the hull and far along the halls, and measuring it found that nine in ten bad detours have nowhere legal to cut, which points back at the packing rather than at the pass. Earlier: `company-ship-deck-adoption.md` has shipped and been folded into `ship-interiors-nouns.md`: the Barracks and Mech Lab are room views on one generated company ship, and no constant scene layout remains. The open frontier is what a deck holds — `facility-room-themes.md` and `ship-deck-family.md` — and letting the player choose the hull it is generated from.

The first two stories are sequenced and the second depends on the first.
`deck-capacity-upgrades.md` is gated on an economic owner, and
`boarding-deck-missions.md` is gated on a mission model.

| Story | Status | Outcome |
|---|---|---|
| `ship-deck-family.md` | IN PROGRESS | Deck, rooms and circulation are integrated, and circulation is looped rather than left a tree. Owes the transverse bulkhead chokepoint sequence and the breach point. Its link measurement leaves a question for the packing: a third of the hull is empty aft while the bow is packed too tight for any corridor to be added. |
| `facility-room-themes.md` | IN PROGRESS | Every shipboard purpose now has its own fitting; nothing ships on the generic aisle treatment any more. The fill defect the story existed to name is fixed at the source, and the follow-on defect it exposed is written up as a law: a line of fixtures across a room is a wall unless it is told not to be, which walled a galley and a sick bay off from their own ships without either of them looking wrong. Still owes the empty-region instrument that can tell an argued aisle from an abandoned middle. |
| `company-ship-selection.md` | IN PROGRESS | Founding, transfer, the refit fee and the loss toll all land: the player picks the company ship, pays the yard to move it, and is displaced - and cut down - when it does not come home. Left: whether named officers are at risk with her. |
| `deck-capacity-upgrades.md` | PROPOSED | Make facility capacity spatial and let a bounded upgrade transaction change the room. Needs a named economic owner first. |
| `boarding-deck-missions.md` | PARKED | Generate hostile prize decks for boarding once a mission model owns objectives and extraction. |

Historical implementation slices belong in `shipped.md`.
