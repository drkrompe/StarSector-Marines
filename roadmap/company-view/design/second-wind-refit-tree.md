# Second Wind refit tree

Status: ACTIVE — direction. The band model, the cores, the mess track, the combat refit tracks, the Ship Systems card and the deck's sealed reserves are implemented, with a first pass of the Deficit dressing for art review, and each reserve is furnished and can be opened on the deck; the facility tracks that open them, their passives, Improved-tier dressing and mech passives are not.
Written: 2026-10-20
Updated: 2026-10-21 — the reserves are furnished at their footprints and open where they stand when the hull says so; the facility tracks still have to say it.

The Engineering tab today sells a short list of one-off yard orders: the
workshop chain (750 / 3,000 / 4,500), the second bay (8,000), three Mech Lab
berths (5,000 each), the bunkrooms (4,000), the distribution circuits (3,000)
and the mess (2,500) — about 46,000 credits in all, nearly all of it spent
before the mid-campaign. Only the mess and the workshop tooling change anything
past presentation and capacity. This doc sets a long upgrade path for the
founding hull that soaks up credits for the whole campaign. Each purchase is a
**passive**, in the campaign layer or in battle. The ship starts below par,
repairs bring her to neutral, and upgrades take her past it.

It extends `company-view-nouns.md` (Engineering, the yard-order authority) and
`ship-interiors-nouns.md` (room program, form vs. state). It does not replace
them.

## Three bands per track

Every track reads on one scale the player learns once:

| Band | Meaning | Reads in Engineering as |
|---|---|---|
| **Deficit** | the battered start; a real, small penalty | red glyph, the penalty named |
| **Restored** | neutral: the numbers every other company and every evidence harness uses | no glyph (healthy rooms stay quiet) |
| **Improved I / II** | past neutral; a named bonus | upgrade pip(s) on the room |

Neutral is the ship's calibration point. Mission balance, `commanderEvidence`,
scenes and matchups all run at neutral, so a Deficit or an upgrade is a known
offset from tuned ground, not a new tuning surface. Only the campaign company
aboard the active converted Second Wind ever leaves neutral. Allies, enemies,
debug companies and fixtures do not, which is the rule the mess already follows.

**Battle deficits are real but cheap to clear.** Jangala and Relief were tuned
at neutral, and the opening must not pay for the ship's injuries. Every battle
deficit therefore clears with a Restored-tier order priced and gated like the
existing post-Relief orders (2.5–5k, available after Relief). The existing
mess, bunkroom and circuits orders are the Deficit → Restored step of their
tracks; their prices and gates stand. Campaign-layer deficits (healing,
upkeep) may last longer, because they cost time and money rather than a fight.

**The mess is re-based** (owner, 2026-10-20). Field rations: morale recovery
−15%. Restored mess: neutral. Proper galley: +15%. Galley and cold store:
+25%, today's shipped value, now the top of the track. The early game is
harder; the ceiling is unchanged.

## Core systems gate facilities

Facilities sit behind tiers of the ship's own plant. The cores are the spine of
the tree and the facilities are its branches. Gating is by tier, not a budget:
a paid room never sits dark (owner, 2026-10-20).

| Core | Tiers (Deficit → Restored → I → II) | Lock reason it supplies | Its own passive |
|---|---|---|---|
| **Power** | Emergency → Distribution restored (the existing 3,000 circuits order) → Reactor overhaul → Auxiliary plant | "no power to the compartment": anything with machinery or screens | none; power is the gate, so the circuits order stays honest |
| **Life support** | Degraded scrubbers → Restored → Expanded → Closed-loop | "no atmosphere": anything people live or train in | Deficit: WIA recovery +1 day. Closed-loop: supplies upkeep (see Fleet stats) |
| **Structure** | Faulty hull → Pressure-tested → Reinforced frames | "compartment sealed" / "frames won't take the load": reopens the new compartments; reinforced frames carry the large ones (range, pool, track, heavy gantries) | Reinforced: hull and armor via the conversion hull mod (vanilla-visible) |
| **Compute** | Crashed cores → Restored → Processing cluster → Tactical core | "no processing": simulators, holo-tank, briefing intel, fire-control calibration | none; a gate like Power |
| **Fabrication** | the existing workshop chain (Buried → Cleared → Repaired → Equipped), plus Precision | "no tooling": production, mech and vehicle work | existing reclamation and 20% supplies discount; Precision adds a part-quality or time benefit |

A facility tier names the core tiers it needs. Engineering shows a locked tier
with its reason in Castle's voice ("the range has no power", "the frames won't
take a pool") and a link to the order that clears it. The tree then reads as
"what do I buy next to reach the range". More lock reasons can join as their
own cores when a facility needs one (sensors, fuel, coolant). Each is a core
with tiers, never a one-off flag.

### Cores restore the interior's look

Core tiers are what make the ship look like a ship again. Today the corridors
and structural rooms are drawn clean. That clean state becomes the **Restored**
look, and the battered start gets a Deficit dressing over the same plan:

| Core at Deficit | What the deck shows |
|---|---|
| Power | emergency lighting (already there for structural rooms), dark panels, hanging conduit |
| Life support | condensation and grime on walls and floors, scrubber grilles stained, haze in the dead air |
| Structure | buckled panels, braced frames, debris decals, patched hull plating in corridors |
| Compute | dead consoles and terminals, cable runs pulled out of the overhead |

Each core's Restored order clears its own dressing everywhere on the deck, so
the player watches the ship come back core by core. Improved tiers add visible
upgrade: better lighting, fresh deck paint, running displays. This is the
form/state split from `company-ship.md` again. Dressing is state laid over
fixed form, so it never moves a room or a door. It also reuses the damage
vocabulary (damaged props, rubble decals) the d-mod damage model wants. It
needs new battered corridor and wall art; the clean art already shipped is the
target, not something to replace.

The deck reads the record's restored cores (`ShipCondition.restoredCores`) as a
per-core flag (`CoreDressing`) and applies it to the running deck, so buying a
core's Restored order clears its look without laying the ship out again. The
first-pass art is one sheet, `ship-dressing`; Improved-tier dressing (better
lighting, fresh paint, running displays) is not built.

## Facilities and their passives

Effects are grouped by where they land. The figures are starting proposals
for a balance pass, not tuned values.

### Battle layer: infantry (frozen at launch, shown on briefing and squad card)

| Facility | Deficit | Restored | Improved I | Improved II |
|---|---|---|---|---|
| **Mess / galley** (exists) | field rations: morale recovery −15% | sealed mess restored: neutral | proper galley: +15% | galley + cold store: +25% |
| **Firing range** (new compartment) | — (absent is neutral) | lanes open: enables range trials aboard (today refused: "a ship without a range") | target machinery: infantry primary accuracy +3% | simulator lanes (Compute): +6% |
| **Gymnasium / indoor track** | — | open: neutral | conditioning: infantry move speed +3% | track loop: +5%, and faster recovery from suppression and stagger |
| **Briefing room** | — | open | plot table: first action against a new threat faster (the experience-tier axis, as a flat company step) | holo-tank (Compute): mission intel shown in the briefing (enemy composition, garrison counts) |
| **Sick bay** (new) | no sick bay: none | open: neutral | surgery: a share of would-be KIA come back WIA | trauma ward: a larger share; stacks with Field Medicine, not with itself |
| **Chapel** | — | open: neutral | services: break threshold eased for the first casualty-ceiling step | — |

### Battle layer: mechs and vehicles

Mechs are already company assets with visible fittings in the Mech Lab, so a
ship passive shows on the mech's sheet there as well as on the briefing.

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Mech Lab calibration** (Compute) | neutral | fire-control calibration: mech weapon accuracy +3% | tactical core link: +6%, and faster target reacquisition |
| **Mech Lab coolant plant** (Power) | neutral | flux dissipation +10% | +20% |
| **Mech Lab armor shop** (Fabrication, Structure) | neutral | section armor +5% at deployment | +10% |
| **Vehicle bay** | neutral | — | — |

Player-side vehicles are not deployable yet (`c12-the-debug-company.md` waits on
them). The vehicle bay keeps its track so the passives exist when they are:
engine tuning (speed), turret calibration (accuracy), field repair (integrity
between battles).

### Campaign layer (outside battle)

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Sick bay / rehab pool** | neutral heal time | WIA heal −25% (multiplies with Recovery Planning, floored) | hydrotherapy pool: −40%, and a crippled-wound class heals instead of discharging |
| **Rec center / lounge** | neutral | replacements arrive in the top half of their aptitude band | shore-leave deck: the replenishment fee drops |
| **Wardroom / staff college** | — | officer XP +15% | +30% |
| **Brig / interrogation** | — | prisoners from a battle yield intel on the next contract in that system | — |
| **Hydroponics** (Life support) | — | supplies upkeep −5% | −10% |
| **Boat bay services** | — | grounded-boat recovery one day faster | deck crane: boat and Hermes material bills −15% |
| **Mech Lab field repair** | — | damaged mechs repair between missions faster | spares rack: lost mounts refit cheaper |
| **Holds / bonded stores** | — | more cargo via the conversion hull mod | shielded stores |

### Combat refit: the hull as a fighting ship

The founding hull fights in ordinary fleet battles too, so her own figures are
tracks of the tree (owner, 2026-10-20). They go through the conversion hull mod,
so the base game's refit screen shows them and every fight she is in uses them.
**They are a liability for a long time** (owner, 2026-10-20): each starts below a
stock Cerberus, and its Restored tier waits on the cores, flux on the reactor
overhaul.

| Track | Deficit | Restored needs | Improved I | Improved II |
|---|---|---|---|---|
| **Hull** | hull −20%, armor −15% | Structure restored | hull +15%, armor +10% (tooling) | +30% / +20% (auxiliary plant) |
| **Flux** | capacity and dissipation −20% | reactor overhaul | +15% / +10% (auxiliary plant) | +30% / +20% (and processing cluster) |
| **Sensors** | strength −30% | Compute restored | +25% (processing cluster) | +50%, profile −25% (and auxiliary plant) |
| **Drive** | speed and acceleration −10%, burn −1 | Power and Structure restored | speed +10%, acceleration +15% (reactor overhaul) | +20% / +25%, burn +1 (auxiliary plant) |

The catalog of tiers, prices and requirements is `RefitOrder`; what a band is
worth is `ShipCondition`.

### Fleet stats (vanilla, through the conversion hull mod)

`MercenaryCompanyConversion` is already a hull mod on the variant. Its
`applyEffectsBeforeShipCreation` can carry the tree's fleet-stat effects. The
base game then shows them on the refit screen and applies them everywhere
without our code. Candidates: supplies per month (Deficit: +25% for a battered
hull; Closed-loop life support: −15%), CR recovery, burn level (drive refit),
sensor strength, cargo. Each is one `stats.*Mod().modify*` keyed by a saved
receipt.

## What this must keep true

- **Legibility law** (`progression-nouns.md`). A squad's fighting quality is
  fully determined by what the player can see. A ship passive is a
  company-wide, named modifier like Tactical Cover. It is listed in Engineering
  and on the briefing, and frozen into `CampaignMarineDeployment` at launch, the
  way `CompanyShipRestoration.moraleRecoveryAboard` already is. It never varies
  per squad. The frozen value becomes one *ship-condition* record, not a
  growing argument list. Mech passives also show on the mech's Mech Lab sheet.
- **An upgrade changes the room or it is not an upgrade** (law 5). Every tier
  has visible deck change: lanes and traps, a pool, machinery, lights, cleaned
  corridors. Upgrades that only change a number go in the hull mod's vanilla
  stats, where the refit screen shows them.
- **The plan is fixed through restoration.** New compartments are reserved at
  conversion as sealed voids in `DeckArrangement.SECOND_WIND`, the way the lance
  bay was. Paying for a repair never moves a room. The Second Wind is a frigate,
  and `programFor` grants a range, sick bay, lounge and gym only from destroyer
  size. These rooms are the founding hull's exception, like her lance bay and
  54 billets, not a change to the general program.
- **Second Wind only** (owner, 2026-10-20). Receipts are saved under the hull's
  id. A company that moves to another hull gets that hull's ordinary facilities
  and loses these passives for future launches, and keeps the receipts for
  when it comes back. A general facility-fit model for other hulls is later
  work on `company-ship.md`'s Fit axis, not part of this tree.
- **One order, one review.** Each tier is its own reviewed yard order:
  inspection spends nothing, payment rechecks gate, funds, home and dock.

## Deck capacity

Measured 2026-10-20 on the converted Second Wind (106×79, the real Cerberus
outline) over 200 layout seeds. The production seed differs per save, because
it mixes the company's seed with the hull's fleet-member id, so a facility
must fit every layout, not most of them.

**What the deck has.** The hull encloses 6,347 cells. The 22 programmed
rooms take 3,025, passages 830–950, and the pocket lockers 145–250. The rest
is bulkheads and fragments: the aft port corner, the starboard aft block,
the starboard waist beside the spine, and the tapered forward pods. The pods
are too narrow for anything over about 10×6.

**At the existing recipe footprints, almost nothing fits.** Laid after the
program, alone: the 30×8 L-shaped range fits 1 layout in 5. A 14×10 gym, sick
bay or briefing room usually fits, but in the worst layout only one 14×10 room
fits at all, and none once a range is down. The 16×12 lounge fits 38 of 40.
All eight together never fit (0/40).

**What is reserved.** Five sealed compartments, at frigate footprints, place
on all 200 layouts. Across those layouts no programmed room's position,
floor, furniture, doors or berths change, and the envelope stays 106×79:

| Reserve | Footprint | Shares with | Where it lands |
|---|---|---|---|
| Firing range | 16×6, three short lanes | — | starboard waist beside the spine (72%), else aft |
| Gymnasium | 12×8 | indoor track (a loop round its edge) | aft port corner (197/200) |
| Sick bay | 10×6 | rehab pool | starboard aft block (82%), else aft port |
| Rec lounge | 8×6 | chapel (a quiet end) | starboard aft (75%), aft port (20%) |
| Brig | 6×6 | — | starboard waist (79%), else aft |

**Facilities that take no new compartment** go into rooms already on the
deck, and their tiers dress that room:

- the briefing room into the **mess**: a plot table, then a holo-tank, at one
  end;
- hydroponics into the **provisions** store: grow racks;
- the wardroom / staff college into the **mess** as well (an officers' end).
  The owner should confirm this one; the lounge is the other candidate.

Boat bay services, the Mech Lab tracks and the holds already have their rooms.

**How they are reserved.** `DeckArrangement.SECOND_WIND` lists the five as
reserves. They are not part of `programFor`, so the general program and the
deck's size are untouched. They are laid after every programmed room and
before the pocket fill, so the same random stream reaches the program with
or without them. A reserve is furnished like any room and then sealed:
stripped, salvage left on the bare deck, pressure hatches on its doors, out
of service. Engineering lists it as *sealed / reserved for refit*; the ship
comparison does not count it. A refit story opens one where it stands.
`SecondWindReservesEvidence` (`generatedSpaceEvidence`) holds all of this
over 60 layouts.

**Opening a reserve.** Each reserve is furnished for its facility at its
reserved footprint, sealed or not, so its fill is the same either way and
opening it changes only whether it is stripped and closed. Which reserves are
open is part of the hull's restoration state, the same record that already
says which wrecked rooms are repaired (`ShipRestoration.openReserves`); every
reserve is closed until something names it. An opened reserve is in service:
its hatches work, its fixtures publish their places and Engineering lists it
as a working room. The facility track whose Restored tier is bought is what
names it; until those tracks exist, nothing does.

What each opened room is:

| Reserve | Furnished as | Publishes |
|---|---|---|
| Firing range 16×6 | three 10-cell lanes, shut deck downrange with a trap behind each target; a range control and prep bench beside the run to the line where the hatch leaves room | practice at the firing line |
| Gymnasium 12×8 | a running loop round the bulkheads, cardio and a press inside it, a weights rack and a mat | exercise (recreation), on the loop as well as at the equipment |
| Sick bay 10×6 | up to three beds across a stretcher aisle from the nurse station, cabinet and sink; a narrow rehab pool in a walkable coping ring at the end with fewer hatches | treatment at the beds, the station and the poolside; the medic's work |
| Rec lounge 8×6 | a sofa and low table, a card table with stools, and at the quieter end a chapel of two pews facing a small altar | unwind (recreation), the pews included |
| Brig 6×6 | two cells, each a bunk and a head behind bars, a bulkhead between them, on the side with fewer hatches; a desk and a property locker outside | one guard post facing the bars; the bunks publish no rest |

These are built from the furniture the other ship rooms already use. The
chapel and the brig have no art of their own yet: the altar is a low table
with a planter, the pews are sofas and the bars are the industrial fence run.

If a later program change moves rooms, the reserve evidence fails before a
save notices; the same evidence opens every reserve on the same layouts and
fails if anything moves or a range lane is crossed.

## Credit curve

The current total is ~46k. A full tree of five cores × 3 tiers and ~14
facility tracks × 2–3 tiers is about 45 orders. As a shape: Restored-tier
orders at 2.5–8k, Improved I at 10–20k, Improved II at 25–50k, for roughly
450–600k in all. The price ladder must be anchored to measured campaign income
(credits per contract after upkeep), so that the Improved II tier of any one
track takes several missions of savings and the whole tree is a late-game goal,
not a checklist. That measurement comes first in the balance story.

## First slices

1. **Deck capacity survey.** Done: five sealed reserves, with the shares and
   placements recorded in *Deck capacity* above.
2. **Ship-condition record and the band model.** Replace the single morale
   float with one frozen record, re-base the mess, and add the Engineering
   band display and the briefing line.
3. **Core gating.** Power, Life support, Structure and Compute tiers above the
   existing orders, with lock reasons in Engineering.
4. **Deficit dressing.** Battered corridor and wall art per core, cleared by
   each core's Restored order.
5. **Firing range**, the first new facility: compartment, range trials aboard,
   accuracy tiers.
6. **Mech Lab passives**, the first of them through the mech sheet.
7. **Hull-mod fleet stats** (supplies, CR) for the campaign-layer deficit and
   its recovery.
8. Remaining facilities, one story each.
