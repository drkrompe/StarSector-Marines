# Second Wind refit tree

Status: ACTIVE — direction. The band model, the cores, the mess track, the combat refit tracks, the Mech Lab tracks and their mech passives, the Ship Systems tree and the deck's sealed reserves are implemented, with a first pass of the Deficit dressing for art review. Each reserve is furnished and opened by its facility track; the range, gym, sick bay and lounge carry passives through both Improved tiers. Hydroponics, the wardroom, the briefing room and the holds are tracks fitted into existing rooms, with their supplies, officer-XP, cargo and garrison-intel passives and a first pass of their room dressing for art review. The brig's improvements, the plot table's own battle passive, Improved-tier dressing of the cores and the reserves (the Mech Lab's included) and the vehicle bay's passives are not built.
Written: 2026-10-20
Updated: 2026-10-21 — the facilities fitted into existing rooms dress them per tier; the wardroom and briefing room need the mess in service.

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

**A reserved facility's neutral is its sealed compartment.** Other companies have
no range or sick bay aboard a frigate, so the sealed room is what costs nothing,
and opening one is already a small gain. Every hull but the founding one reads
these tracks as sealed, not as opened.

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

Engineering draws this as a tree flowing downward (owner, 2026-10-21: "an
upgrade tree similar to our commander skill tree, but actually connected
together flowing downward"), entered from core icons on the deck rather than a
list of buttons. The five cores stand side by side under one bar; every other
track is a row of its own down its parent core's spine, its tiers drawn left to
right inside the row. Tiers do not share a level across the tree: with some
twenty tracks and sixty tiers, one row of tier squares per level could not fit
(owner, same day: "consider not placing every 'level' of the tree on the same
level"). Only what a track's *next* tier needs from other cores is marked, as
small core icons at the row's end (all of it is listed on its card), and none
of it is drawn as cross-links, because crossing lines would turn the tree into
a wiring diagram. The modal carries no title, emblem or guide text, only the
progress count and the way back to the deck.

The tree should feel like *X and Y to get Z*, not a ladder (owner,
2026-10-21): a first pass funnelled nearly everything through Power. So
every opening and Improved I tier needs two different cores, and the larger
rooms and the top tiers need three. Each requirement has a reason in the
ship (a sound frame for racks and mounts, the thermal loop to cool the
cluster, the workshop to cut plate):

| Tier | Needs |
|---|---|
| Life support restored | Power R, Structure R (sealed bulkheads) |
| Compute restored | Power R, Life support R (cooling) |
| Power I (reactor overhaul) | Structure R, Fabrication R (new trunk cut in the workshop) |
| Compute I (cluster) | Power I, Life support R, gamma core |
| Power II (auxiliary plant) | Fabrication I, Compute I (load balancing) |
| Compute II (tactical core) | Power II, Fabrication I, beta core |
| Range | Power R, Structure R, Compute R (target machinery) |
| Sick bay | Life support R, Power R, Compute R (diagnostics) |
| Gym, lounge | Life support R, Structure R |
| Brig | Structure R, Power R (cell locks) |
| Range I / II | Fabrication R (targets and traps cut in the workshop) + Power I / Compute I (simulators) + Power I + Fabrication I |
| Gym I / II | Fabrication R (press and rack) + Power R / Compute R (lap boards) + Power I + Fabrication I (sprung decking) |
| Sick bay I / II | Power I (steady theatre power) + Fabrication R (instruments) / Compute I (monitored beds) + Life support R (isolation loop) + Power I |
| Lounge I / II | Compute R (screens, library) + Power R / Compute I (projected shore leave) + Power I + Fabrication R |
| Mess galley / cold store | Power R + Life support R / Power I + Compute R (stock control) |
| Hull R / I / II | Structure R + Fabrication R / Fabrication I + Power I / Fabrication I + Power II + Compute I |
| Flux R / I / II | Power I + Structure R / Power II + Compute I / Power II + Compute II, alpha core |
| Sensors R / I / II | Compute R + Structure R (mast mount) / Compute I + Power I / Compute II + Power II |
| Drive R / I / II | Power R + Structure R / Power I + Compute R (governor) / Power II + Structure R + Fabrication I |
| Mech calibration R / I / II | Compute R + Power R (bench) / Compute I + Fabrication R / Compute II + Power I + Fabrication I |
| Mech coolant R / I / II | Power R + Life support R (scrubber loop) / Power I + Structure R (chiller mount) / Power II + Life support R + Compute I |
| Mech armor shop R / I / II | Fabrication R + Structure R / Fabrication I + Power R (plate press) / Fabrication I + Structure R + Power I |
| Mech field repair R / I / II | Fabrication R + Power R / Fabrication I + Compute R (diagnostics) / Fabrication I + Structure R + Compute I |
| Hydroponics R / I / II | Life support R + Power R (water feed, grow lights) / Power I + Life support R / Life support R + Power I + Compute I (climate control) |
| Wardroom R / I / II | Life support R + Structure R / Compute R (war-game console) + Life support R / Compute I + Power I + Life support R |
| Briefing room R / I / II | Compute R + Power R (the screen) / Compute R + Structure R (the table's frame) / Compute I + Power I + Life support R (the tank, run off the processing cluster) |
| Holds R / I / II | Structure R + Power R / Structure R + Fabrication R (racking) / Structure R + Fabrication I + Power I (bonded stores' climate) |

Power restored, Structure restored and the mess's opening order need nothing:
they are where a battered start begins. The table is `RefitOrder`; this is
its intent, to be tuned in play. Each system's
card opens with Castle explaining how it works aboard her. That is the point of
the view: learning the ship as well as buying from her.

The tree is a modal over the live ship, not a page in place of it, and a
purchase is watched (owner, 2026-10-21: "I want the player to see the upgrade
occur, to see the room change"). The camera flies to the room, the yard works
behind a curtain, and the room is revealed changed (`RefitReveal`). The cut is
deliberate: opening a compartment lays the deck out again, and a curtain
reads as the yard's days of work where an animated swap would show a blank
deck and a re-mustered crew. A change that is only dressing, a core brought
up from Deficit, keeps the same deck, so it plays without the curtain: the
order is paid as the camera arrives and the core's look clears across the
running deck as a wave from its source room, lamps and screens stuttering on
as it reaches them. The reveal waits for the wave before naming the result,
so the player watches the before turn into the after in one shot (owner,
2026-10-21: "Player still sees the A/B happen and it's an effect").

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
| **Firing range** (reserve) | sealed: neutral | lanes open: range trials aboard (refused without one: "a ship without a range") | target machinery: infantry primary accuracy +3% | simulator lanes: +6% |
| **Gymnasium / indoor track** (reserve) | sealed: neutral | open: infantry move speed +2% | conditioning: +4% | track loop: +6%, and hits and near misses drain 10% less morale |
| **Briefing room** | — | open | plot table: first action against a new threat faster (the experience-tier axis, as a flat company step) | holo-tank (Compute): mission intel shown in the briefing (enemy composition, garrison counts) |
| **Sick bay** (reserve) | sealed: neutral | open: one in ten would-be KIA come home WIA | surgery: a quarter | trauma ward: two in five; stacks with Field Medicine |
| **Chapel** | — | open: neutral | services: break threshold eased for the first casualty-ceiling step | — |

### Battle layer: mechs and vehicles

Mechs are already company assets with visible fittings in the Mech Lab, so a
ship passive shows on the mech's sheet there as well as on the briefing. The
Mech Lab has no deficit: an absent shop is neutral, like an absent range.

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Mech Lab calibration** (Compute) | bench: neutral | fire-control calibration: mech weapon accuracy +3% | tactical core link: +6%, and torso traverse +25% (onto a new target faster) |
| **Mech Lab coolant plant** (Power) | neutral | flux dissipation +10% | +20% |
| **Mech Lab armor shop** (Fabrication, Structure) | neutral | armor capacity +5% at deployment | +10% |
| **Vehicle bay** | — | — | — |

The battle passives are one frozen value per deployed mech (`MechRefit`), set
over each owned mech's deployment when the launch takes the active lance
(`DetachmentResolver`). The deployment keeps its fit's own frame beside it, so
the refit is never folded in twice: armor and dissipation scale the frame the
mech lands on, the accuracy factor multiplies every mount's hit roll with the
head-loss penalty, and the traverse factor scales the torso's turn rate. A mech
the company does not own is neutral, and so is every mech off the founding
hull. A recorded fixture carries the refit with the frame.

Player-side vehicles are not deployable yet (`c12-the-debug-company.md` waits on
them). The vehicle bay is a track with no orders, so its passives have a home
when they are: engine tuning (speed), turret calibration (accuracy), field
repair (integrity between battles).

### Campaign layer (outside battle)

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Sick bay / rehab pool** | heal time: not offered | — | — |
| **Rec center / lounge** (reserve) | open: no cargo replacement arrives Limited | rec centre: and 30% arrive Gifted (20% neutral) | shore-leave deck: and 10% Exceptional, 35% Gifted |
| **Wardroom / staff college** | — | officer XP +15% | +30% |
| **Brig / interrogation** (reserve) | open: the cells and a guard post | not offered: waits on a battle that takes prisoners; then a prisoner yields intel on the next contract in that system | — |
| **Hydroponics** (Life support) | — | supplies upkeep −5% | −10% |
| **Boat bay services** | — | grounded-boat recovery one day faster | deck crane: boat and Hermes prices −15% |
| **Mech Lab field repair** (Fabrication) | neutral | repair clock −25% | spares rack: repair clock −25%, and a weapon assembly fabricates at −25% on every line, which is what refitting a lost mount costs |
| **Holds / bonded stores** | — | racked holds: cargo +20 via the conversion hull mod | bonded stores: cargo +40 |

How each passive lands. The range and the gym reach the fight as one named
unit modifier on the marines of the company's own squads (accuracy, move speed
and morale drain), read off a record frozen with each squad at launch with the
mess's morale recovery, so a refit bought mid-operation changes the next launch,
never a battle underway. The sick bay acts where the battle is settled: a marine
the battle counted as killed rolls their fate as before, and if it comes up
killed, an independent draw against the bay's share brings them home wounded.
Nobody wounded or missing moves, and a better bay saves everyone a lesser one
did. The lounge acts on the cargo marines who take a billet, whose fixed roll
is read against a higher intake; recruits founded with a squad are untouched.

Some rows wait on a system that does not exist yet, and stay unoffered rather
than faked. A wound heals in one day, and Recovery Planning is already floored
at a day, so a sick bay heal-time tier would change nothing; it waits on
longer wounds, and the bay's tiers act on fate instead. Replacements cost cargo
marines, not a fee, so the lounge's top tier raises the intake again rather
than discounting a price. The brig's interrogation waits on prisoners, not on
intel. The contract half exists: the holo-tank's garrison read (`GarrisonIntel`)
is the operation's own roster and could be shown, or added to, for a contract a
prisoner knows about. The other half does not exist. No battle takes anyone
alive: a defender is fought until killed, or is still on the field when an
objective ends the battle, and nothing yields, is secured or is carried to a
boat. `MissionOutcome` records nothing about the defenders at all, so the
company never holds a prisoner for the brig to question. Counting the defenders
left standing at a win as captives would be the faked tier the owner ruled out.
Interrogation is offered once the battle has a capture mechanic: a defender who
gives up (broken, wounded, cut off), is secured by a marine and brought out on a
boat, recorded on the outcome by faction and system, and held aboard until
questioned. Even then it has to show what the holo-tank does not, since
Briefing II already shows every operation's garrison: where the command post
stands, or what reinforcement the defenders can call. Until then the brig opens
its cells and stops there.

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
  and on the briefing, and frozen into `CampaignMarineDeployment` at launch as
  one record, `HullWelfare` (mess, range, gym), carried by every seat's squad
  tag and fixture commitment rather than a growing argument list. It never
  varies per squad. Mech passives also show on the mech's Mech Lab sheet.
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

**What is reserved.** Five sealed compartments. Four are frigate footprints
laid after the program, so they never move a programmed room's position,
floor, furniture, doors or berths. The gymnasium is the cruise-ship layout at
28×14, which fits none of 200 layouts when laid last, so it is laid ahead of
the mess in the aft port block and the rooms after it find room around it; the
loading bay goes forward. The envelope stays 106×79.

**Her layout is owned, not rolled.** The company has one ship and she has one
deck, so her arrangement pins the layout seed (42): every campaign lays the
same rooms in the same places, and the layout is checked once rather than
hoped for across seeds. The placement shares below are from the unpinned
survey that chose it:

| Reserve | Footprint | Shares with | Where it lands |
|---|---|---|---|
| Firing range | 16×6, three short lanes | — | starboard waist beside the spine (72%), else aft |
| Gymnasium | 28×14, laid ahead of the mess | two-lane track round the room, equipment floor, pool | aft port block |
| Sick bay | 10×6 | rehab pool | starboard aft block (82%), else aft port |
| Rec lounge | 8×6 | chapel (a quiet end) | starboard aft (75%), aft port (20%) |
| Brig | 6×6 | — | starboard waist (79%), else aft |

**Facilities that take no new compartment** go into rooms already on the
deck, and their tiers dress that room:

- the briefing room into the **mess**: a briefing screen, later a plot table
  and a holo-tank, at its dining end;
- hydroponics into the **provisions** store: grow racks;
- the wardroom / staff college into the **mess** as well (an officers' end).
  The owner should confirm this one; the lounge is the other candidate.

Boat bay services, the Mech Lab tracks and the holds already have their rooms.

These are tracks of their own with no reserve to open, so buying a tier
changes no compartment's state. Their passives:

- **Hydroponics** lowers her own supplies per month (−5%, −10%) through the
  conversion hull mod, so the refit screen shows it under *Ship's stores*.
- **Wardroom** scales the XP an officer earns from a mission (+15%, +30%),
  applied after the natural-leader bonus and read when the mission resolves,
  the way the scrubbers' wound day is. The briefing names it once it pays.
- **Holds** wins back cargo the conversion took (+20, +40), also through the
  hull mod.
- **Briefing room**: the **holo-tank** (Improved II) shows the briefing the
  garrison the operation will face: every defender on the ground, the regulars
  and militia among them, and the most mechs it can field. The headcount is the
  battle's own. Launch and the briefing resolve the mission's target profile
  through one function (`MissionLaunch.targetProfile`), and the estimate is
  `DefenderRoster.forMission` over that profile with nothing held back. The
  total depends only on type, tier, risk and the market's garrison. Mechs are
  priced against the committed landing, so the briefing says *up to*; a mech
  the landing does not draw is fielded as infantry, which makes the regulars
  and militia shown the least of each. It is read when the briefing is drawn,
  like the scrubbers' wound day, and only where the defenders are derived from
  the mission. Debug missions and scripted battles that bring their own force
  (the opening operations, the story arcs, the supply route, the silent colony
  and the civilian rescue) show nothing. The **plot table** (Improved I) is a plot
  table in the briefing corner that the holo-tank is built on, sold for credits
  alone, and it changes nothing in battle yet: its passive, a faster
  first action against a new threat, needs a reaction axis no seat carries.
- **Boat bay services** is not a track yet: a boat grounded in battle has no
  timed recovery to shorten, and the deck crane's discount needs the boat
  bill to read the condition record.

**Their tiers dress the room.** Each tier is presentation laid over the
room's fixed fill, the way a core's Deficit dressing is: the deck reads the
record's bands (`ShipCondition.facilityDressing`) as a tier per facility
(`FacilityDressing`) and applies it to the running deck, so buying a tier
changes the room at once without laying the ship out again. Every tier's
pieces are planned once from the deck and its seed and a tier only chooses how
many are drawn, so a tier adds pieces and never moves one already standing.

| Facility | Room | Restored | Improved I | Improved II |
|---|---|---|---|---|
| Hydroponics | provisions store | three grow trays along the bulkheads, away from the hatch | six | ten |
| Wardroom | mess, the beam half away from the hatches at the dining end | a bookcase screen across the lane before the last rank of tables | a sideboard on the end bulkhead, a planter | the staff college's wall screen on the long bulkhead, a second planter |
| Briefing room | mess, the far (dining) end | a display on the end bulkhead and a lectern beside it; the mess tables are the seating | the plot table in the briefing corner | the holo-tank's processor and console beside the briefing screen |
| Holds | hold | a tally desk by the hatch and a stowed pallet | a rack with cargo in its bays | two more racks, or shelving where a rack does not fit |

The dining end is the end away from the galley, so both mess facilities share
it: the briefing screen faces the whole room down its length, and the
wardroom is screened off in the half of the beam away from the hatches.
Because the mess is their room, the wardroom's and briefing room's Restored
orders need the mess in service as well as their cores; a wrecked room carries
its own condition art and is never dressed.

The pieces stand only on open floor of their own room, off every work point
and seat and two cells clear of every doorway; a wall piece backs onto
bulkhead along its whole length with open deck in front of it, a free-standing
rack keeps open deck all round, and with every tier standing every work point
and reachable cell of the room stays reachable round them. The crew walk the
deck generation made, so that is what keeps their way to work clear of a rack.
`SecondWindFacilitiesEvidence` (`generatedSpaceEvidence`) holds this over 40
layouts, every tier changing its room on each.

The first pass reuses shipped furniture: grow trays are a utility shelf with
a lounge planter in each cell, the wardroom uses the lounge's bookcase,
refreshment counter and media wall, the briefing room its own display and
lectern, the hold the main stores' tally desk, pallet and racks. The plot
table is the lounge's card table and the holo-tank the utility server and
console, because the briefing room's own table and the bridge's plot are too
deep for the two cells left at the mess's end. Purpose-made art is still
wanted for a lit hydroponic tray, a wardroom screen panel and table setting, a
plot table that fits a two-cell end, and the holo-tank's projection over it.

**How they are reserved.** `DeckArrangement.SECOND_WIND` lists the five as
reserves. They are not part of `programFor`, so the general program and the
deck's size are untouched. They are laid after every programmed room and
before the pocket fill, so the same random stream reaches the program with
or without them. A reserve is furnished like any room and then sealed:
stripped, salvage left on the bare deck, locked doors, out
of service. Engineering lists it as *sealed / reserved for refit*; the ship
comparison does not count it. Its facility track's Restored order opens it where it stands.
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
names it (`ShipCondition.openReserves`).

What each opened room is:

| Reserve | Furnished as | Publishes |
|---|---|---|
| Firing range 16×6 | three 10-cell lanes, shut deck downrange with a trap behind each target; a range control and prep bench beside the run to the line where the hatch leaves room | practice at the firing line |
| Gymnasium 28×14 | a two-lane running track round the bulkheads; four treadmills, four rowers, two presses and a mat inside it; a 10×4 pool with three loungers and two weights racks on its deck | exercise (recreation) at the equipment, the mat and the racks; unwind at the loungers |
| Sick bay 10×6 | up to three beds across a stretcher aisle from the nurse station, cabinet and sink; a narrow rehab pool in a walkable coping ring at the end with fewer hatches | treatment at the beds, the station and the poolside; the medic's work |
| Rec lounge 8×6 | a sofa and low table, a card table with stools, and at the quieter end a chapel of two pews facing a small altar | unwind (recreation), the pews included |
| Brig 6×6 | two cells, each a bunk and a head behind bars, a bulkhead between them, on the side with fewer hatches; a desk and a property locker outside | one guard post facing the bars; the bunks publish no rest |

These are built from the furniture the other ship rooms already use. The
chapel and the brig have no art of their own yet: the altar is a low table
with a planter, the pews are sofas and the bars are the industrial fence run.

If a later program change moves rooms, the reserve evidence fails before a
save notices; the same evidence opens every reserve on the same layouts and
fails if anything moves or a range lane is crossed.

## Prices and exotic parts

A tier's price is credits (owner, 2026-10-26: dealing in resources is annoying).
The metals, heavy machinery, transplutonics and organics tiers once took from
fleet cargo were folded into their credit prices at the vanilla base prices
(`company-view-nouns.md`, Price); each tier is bought docked at a planetary yard.

The top of the tree is end-game content, gated on exotic parts the player
already hunts for on the sector's outskirts (owner, same day). An exotic part
is a hard requirement: there is no credit-heavy alternative, because everything
below it stays reachable without one. The exotic parts are the alpha, beta and
gamma cores and nothing else for now: **colony items never go into the ship**,
the Threat's and the abyss's special items were tried and dropped, and a relic
part (the owner's "relics") must work in the base game without another mod,
so it waits for a source the game itself provides. The comfort rooms (mess,
gym, lounge, hydroponics, wardroom, holds) take no core, and nothing asks for
one to reach Restored.

| Tier | Core | Why there |
|---|---|---|
| Compute: processing cluster | gamma | |
| Compute: tactical core (gates the military flux grid and sensor suite) | beta | |
| Flux: military flux grid | alpha | |
| Range: simulator | gamma | runs the opposing force |
| Sick bay: trauma | gamma | diagnostics |
| Briefing room II | gamma | threat projection on the holo-tank |
| Mech calibration II | beta | mech fire control |

The physical-plant tops (auxiliary plant, Drive II, Sensors II, mech field
repair II) are where a relic part would go. The cores a tier takes are
`RefitOrder.cores()`; prices are placeholders.

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
5. **Reserved facilities.** Done for the range, gym, sick bay and lounge
   (passives through both Improved tiers); the brig waits on prisoners taken in battle,
   and Improved-tier dressing is not built.
6. **Mech Lab passives.** Done: calibration, coolant plant, armor shop and
   field repair, on the mech sheet and the briefing line.
7. **Hull-mod fleet stats** (supplies, CR) for the campaign-layer deficit and
   its recovery.
8. Remaining facilities, one story each.
