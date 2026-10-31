# Second Wind refit tree

Status: ACTIVE — direction. The band model, the cores, the mess track, the Mech Lab tracks and their mech passives, the reserve hangar, the Ship Systems tree and the deck's sealed reserves are implemented, with a first pass of the Deficit dressing for art review. The range, gym, sick bay and lounge reserves are opened by their facility tracks and carry passives through both Improved tiers. Improved-tier dressing of the cores and the reserves (the Mech Lab's included) is not built.
Written: 2026-10-20
Updated: 2026-10-31 — Alpha scope: only upgrades that make the company stronger are offered.

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

## Alpha scope: only what makes the company stronger

The tree sells a track only if it buffs the company's marines, mechs, vehicles
or boats (owner, 2026-10-31: the rest "are just noise"). The shuttle bay repair
and the Mech Lab berth repairs are yard orders outside the tree and stay. Cut
for Alpha, with their passives and dressing removed from the code:

- the combat tracks (hull, flux, sensors, drive): she flies at a stock
  Cerberus's figures, with no deficit to buy back;
- hydroponics (supplies upkeep) and the holds (cargo);
- the wardroom (officer XP) and the briefing room (the plot table and the
  holo-tank's garrison read);
- the brig, whose reserve stays sealed on the plan, and the empty vehicle bay
  track.

A save's receipts for a cut tier stay in place and are never read. The
sections below keep the kept tracks' design; any cut track would come back as
its own story.

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
| **Life support** | Degraded scrubbers → Restored | "no atmosphere": anything people live or train in | Deficit: WIA recovery +1 day |
| **Structure** | Faulty hull → Pressure-tested | "compartment sealed": reopens the new compartments | none; a gate |
| **Compute** | Crashed cores → Restored → Processing cluster → Tactical core | "no processing": simulators, diagnostics, fire-control calibration | none; a gate like Power |
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
| Reserve hangar R / I | Structure R + Power R / Structure R + Fabrication R |
| Range I / II | Fabrication R (targets and traps cut in the workshop) + Power I / Compute I (simulators) + Power I + Fabrication I |
| Gym I / II | Fabrication R (press and rack) + Power R / Compute R (lap boards) + Power I + Fabrication I (sprung decking) |
| Sick bay I / II | Power I (steady theatre power) + Fabrication R (instruments) / Compute I (monitored beds) + Life support R (isolation loop) + Power I |
| Lounge I / II | Compute R (screens, library) + Power R / Compute I (projected shore leave) + Power I + Fabrication R |
| Mess galley / cold store | Power R + Life support R / Power I + Compute R (stock control) |
| Mech calibration R / I / II | Compute R + Power R (bench) / Compute I + Fabrication R / Compute II + Power I + Fabrication I |
| Mech coolant R / I / II | Power R + Life support R (scrubber loop) / Power I + Structure R (chiller mount) / Power II + Life support R + Compute I |
| Mech armor shop R / I / II | Fabrication R + Structure R / Fabrication I + Power R (plate press) / Fabrication I + Structure R + Power I |
| Mech field repair R / I / II | Fabrication R + Power R / Fabrication I + Compute R (diagnostics) / Fabrication I + Structure R + Compute I |

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
| **Sick bay** (reserve) | sealed: neutral | open: one in ten would-be KIA come home WIA | surgery: a quarter | trauma ward: two in five; stacks with Field Medicine |

### Battle layer: mechs and vehicles

Mechs are already company assets with visible fittings in the Mech Lab, so a
ship passive shows on the mech's sheet there as well as on the briefing. The
Mech Lab has no deficit: an absent shop is neutral, like an absent range.

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Mech Lab calibration** (Compute) | bench: neutral | fire-control calibration: mech weapon accuracy +3% | tactical core link: +6%, and torso traverse +25% (onto a new target faster) |
| **Mech Lab coolant plant** (Power) | neutral | flux dissipation +10% | +20% |
| **Mech Lab armor shop** (Fabrication, Structure) | neutral | armor capacity +5% at deployment | +10% |

The battle passives are one frozen value per deployed mech (`MechRefit`), set
over each owned mech's deployment when the launch takes the active lance
(`DetachmentResolver`). The deployment keeps its fit's own frame beside it, so
the refit is never folded in twice: armor and dissipation scale the frame the
mech lands on, the accuracy factor multiplies every mount's hit roll with the
head-loss penalty, and the traverse factor scales the torso's turn rate. A mech
the company does not own is neutral, and so is every mech off the founding
hull. A recorded fixture carries the refit with the frame.

Player-side vehicles are not deployable yet (`c12-the-debug-company.md` waits on
them). A vehicle bay track returns when they are: engine tuning (speed), turret
calibration (accuracy), field repair (integrity between battles).

### Campaign layer (outside battle)

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Sick bay / rehab pool** | heal time: not offered | — | — |
| **Rec center / lounge** (reserve) | open: no cargo replacement arrives Limited | rec centre: and 30% arrive Gifted (20% neutral) | shore-leave deck: and 10% Exceptional, 35% Gifted |
| **Mech Lab field repair** (Fabrication) | neutral | repair clock −25% | spares rack: repair clock −25%, and a weapon assembly fabricates at −25% on every line, which is what refitting a lost mount costs |

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
than faked. A wound heals in one day, and recovery is floored at a day, so a
sick bay heal-time tier would change nothing; it waits on
longer wounds, and the bay's tiers act on fate instead. Replacements cost cargo
marines, not a fee, so the lounge's top tier raises the intake again rather
than discounting a price.

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
  corridors.
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
gym, lounge) take no core, and nothing asks for
one to reach Restored.

| Tier | Core | Why there |
|---|---|---|
| Compute: processing cluster | gamma | |
| Compute: tactical core | beta | |
| Range: simulator | gamma | runs the opposing force |
| Sick bay: trauma | gamma | diagnostics |
| Mech calibration II | beta | mech fire control |

The physical-plant tops (auxiliary plant, mech field repair II) are where a relic part would go. The cores a tier takes are
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
   (passives through both Improved tiers); Improved-tier dressing is not built.
   The brig has no track for Alpha.
6. **Mech Lab passives.** Done: calibration, coolant plant, armor shop and
   field repair, on the mech sheet and the briefing line.
7. Further tracks only where they make the company stronger, one story each.
