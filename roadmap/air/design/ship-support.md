# Ship support — ships in atmosphere for every mission

Status: ACTIVE

Written: 2026-10-13
Updated: 2026-10-14 — owner: hostile ships are dangerous by design, countered by friendly forces, the company's own ship or keeping theirs busy elsewhere (slice 6); slices 1-5 are built.

Read `air-nouns.md` (**Overhead ships**, **Tenders**), `warship-behaviours.md` and
`command-powers-nouns.md` first. This doc says how a ship gets over a battle in
ordinary play, and what makes having one there fun. The ship itself (hover, guns,
shield, flux, posture, drones) is already built and is not restated here.

## What the owner asked for

Features for ships in atmosphere in campaign missions, with only a thin difference
between campaign and ordinary missions. Ships stay overpowered by design and gated by
context, never balanced by a damage nerf (`warship-on-station.md`).

## Law: one mechanism, thin campaign difference

There is **one** way a ship gets over a battle: a **ship-support power** in the
mission's command deck. A campaign mission and any other mission differ only in what
they hand that mechanism:

| Input | Campaign mission | Debug, scenario or employer mission |
| --- | --- | --- |
| Source | a committed fleet member (a warship or a Shepherd) | a stock hull, or the employer's offered hull |
| Loadout | the member's own fitted variant, frozen at launch | the stock variant |
| Cost at use | supplies, as other powers | free, as other debug powers |
| Writeback | hull damage and readiness to the member | none |

Everything in battle (arrival, station, stance, director, piloting, departure, loss)
is the same code whoever supplied the ship. A mission script that wants a ship (Cold
Account's Onslaught) is an employer source of the same thing, not a second path. The
debug WARSHIP DEBUG picker becomes a debug source on the same seam rather than a
launch-time install.

This follows from the command-power laws: availability comes from committed sources,
the battle roster is frozen at launch and holds no campaign objects (a frozen
`WarshipLoadout`, never a `FleetMemberAPI`), and the simulation owns activation.

## The power: Ship on Station

- **Source.** A committed fleet member whose hull can be a warship body (a combat hull
  with ground-reaching weapons) or a tender (the Shepherd). One source gives one ship;
  two committed capitals are two cards' worth of ship, not one doubled.
- **Targeting.** The player picks a cell: the ground the ship should work. Valid
  anywhere on the map the player has seen; the ship's own rules decide what it fires at.
- **Activation.** The ship is fitted from the frozen loadout, breaks out of the clouds on
  the company's side of the cell a short glide from it (about twelve cells plus half its
  length, so the descent is over the battle and the arrival is quick), settles onto a station over the cell, and works it under its side's
  `MissionStance` (an attacking company's ship supports the assault; a defending
  company's denies the drop). A tender builds there by the same stance director.
- **Pacing.** One activation per source per battle, a bounded station time
  (`stayFor`), then it departs back the way it came. The player can take the helm
  through direct control at any point, as today.
- **Loss.** A ship taken to nothing is lost for the battle, and the campaign
  member takes the damage (slice 2). That risk is the honest price of bringing a capital, and the
  reason commitment is the player's choice.

## What makes it fun in atmosphere

In the order they are worth building:

1. **Arrival is an event.** The ship drops through the cloud deck at the map edge: its
   shadow sweeps in ahead of it and sharpens as it descends, a re-entry glow fades off
   the hull, a pressure wave kicks dust and bends smoke on the ground beneath its path,
   and a deep sonic report rolls across the map. Departure is the same in reverse, a
   climb into the deck. Everything on the ground knows a capital has arrived.
2. **The enemy can bring one too.** A defended world (the target profile's defence
   level and stationed strength) may answer with its own ship on the same mechanism,
   as a mission threat with a warning: the defender's ship on station is the moment
   the battle turns, and the player's answer is its own ship, its tender's flak and
   point defence, or getting under its guns.
3. **Low pass.** A piloted ship (or a scripted one) may make a fast, low pass along a
   line: no station, guns on whatever crosses the line, a much louder wake, the shadow
   at its sharpest. A show of force that costs part of the station time.
4. **The ship answers to the air.** Once aircraft can hurt ships (G3, in progress) a
   defending world's strike craft go for the hull and the ship's flak and drones fight
   back; a carrier hangs back; an escort is the follow-on.
5. **Weather and world.** The planet type already picks the surface palette; it can
   also tint the cloud deck and the re-entry glow, and a heavy-atmosphere world can
   slow arrival. Presentation only.

## Slices

1. **Ship on Station power** (BUILT, 2026-10-13): the power, its
   sources (fleet member, stock, employer), the frozen loadout through the detachment,
   activation, edge entry, station, stance, departure, the debug picker re-pointed onto
   it. Evidence: a behaviour scene and unit tests at the catalog and loadout seams.
2. **Writeback** (BUILT, 2026-10-13): hull and readiness to the committed member through
   mission settlement; see "Slice 2 as built".
3. **Arrival and departure in atmosphere** (built): the descent, shadow, glow, pressure
   wave and sound, presentation only and deterministic from the ship's own clocks. A
   ship that `arriveFromAbove()` drops through a cloud deck about fifty cells up over six
   seconds on an ease-out; any ship that departs climbs back on an ease-in. One number,
   the lift, drives its shadow (further down-sun, softer and thinner when high, closing
   as it settles), the haze over the hull, the cloud streaming astern, the re-entry glow
   (fading over about four seconds), the dust under the hull and the ring sent across
   the ground at touchdown; a climb adds an exhaust wake. Sounds are the game's own
   ids, played through the ordinary warship cue path: a slowed `gate_explosion` on entry,
   a `hit_heavy` thud at touchdown, `engine_accelerate` low at lift-off and higher
   as it climbs, each rate-limited. Any ship that does not enter this way is
   unchanged. The planet tint is wired: `BattleSetup` hands the target profile's surface to
   the simulation (`BattleSimulation.surface`, null reads as bare rock) and the warship renderer
   tints from it each frame. What remains: the power must call
   `arriveFromAbove()` on activation (the debug install does), and a departing ship is
   hidden by its climb well before it leaves the map. Needs a live look for how the
   cloud, glow and dust read over real terrain and under the live camera, and for the
   audio levels.
4. **Hostile ship support** (built): the defender's ship on the same mechanism, offered by
   the target profile, with a warning beat and a ground marker. See *Slice 4 as built*.
5. **Low pass** (built): the order, its flight, fire, cost, bindings and presentation; see *Slice 5 as built*.
6. **The hostile ship comes from somewhere.** Today a defended world above a threshold
   simply has a stock hull. The owner's counters (below) need the enemy's ship to be a
   real ship in the campaign that the player can keep busy elsewhere. The defender's
   source is drawn from an actual fleet of the defending faction in the target's system
   (its largest eligible hull, frozen like the company's), and none is offered when no
   such fleet is present, or when it is engaged, broken or drawn off. Other missions
   hand the same input in by declaration, so the difference stays thin.

## Open questions for the owner

1. Which hulls are sources: every combat hull with a ground-reaching gun, or only
   cruisers and capitals? The draft is cruisers, capitals and the Shepherd, so a
   frigate stays a frigate.
2. Station time: one fixed figure (90 s) for every hull, or by size?
3. Should a lost member be destroyed in the campaign, or only crippled? The draft is
   crippled (hull at a floor, readiness zero), because losing a capital to one ground
   battle is harsher than the fun it buys.

## Slice 1 as built

The `ship_on_station` power (`ShipOnStation`) holds a frozen `ShipSupportSource` list:
each committed cruiser, capital (with a gun that reaches the ground) or Shepherd is one
charge on one card, spent in order. Debug (the briefing picker's friendly pick) and
employer offers (`ship_on_station:<hull>` in the offer ids) feed the same list; a hostile
debug pick is the defender's source (slice 4). Activation needs a cell the player
currently sees, fits the next source at the company's edge (`ShipStationing`), stations it
on the cell, takes the stance from the company's side (ATTACK, or DEFEND for a stationed
detachment), turns on the director and doctrine, and bounds its visit at 90 s for every
hull. Drafted owner answers applied: cruisers, capitals and the Shepherd; 90 s; no
writeback.

A capital's own hover speed made a map-edge approach long: an Onslaught entering at the west
edge took about 74 s to reach a cell 80 cells in. A called ship therefore comes out of the
clouds a short glide from its station instead (`ShipStationing.callIn`), and the same
Onslaught settles in about 21 s. Station time is counted on station, as before.

## Slice 2 as built

Drafted owner answers applied, including question 3 (crippled, not destroyed). A source
frozen from a committed member carries its fleet member id (a string, never the member);
debug and employer ships carry none, which is the whole of their difference: no id, no
result, no writeback. The ship records nothing while it works; the battle keeps every hull
a call put over it (`WarshipSystem.called`, which survives destruction and departure),
`MissionResolver.compute` freezes each as a `ShipSupportResult` on the outcome, and
`apply` settles them through `ShipSupportSettlement` beside the lost-boat strike.

| Case | Hull | Combat readiness |
| --- | --- | --- |
| Committed, never called | unchanged | unchanged |
| Called, survived | prior hull x share left, never below 10% | less the hull's CR-to-deploy |
| Called, destroyed | 10% (or its prior hull if lower), crippled not lost | 0 |
| Debug or employer ship | nothing | nothing |

The readiness figure is the member's own `getDeployCost` (the `ship_data.csv` "CR to
deploy": Onslaught 12%, Conquest 15%, Paragon 20%, Shepherd 20%), the same price vanilla
charges to deploy that hull, so no number is invented. The results screen lists each called
ship with its hull left, or as crippled. Needs a live look: `getStatus().setHullFraction`
and `getRepairTracker().setCR` on a real member (whether the fleet screen shows the hull
and CR as written, and whether a crippled member needs a repair or only supplies).

## Slice 4 as built

The same `ShipOnStation.call`, with the side generalised: `call(source, side, x, y, battle)`
(the company's overload is unchanged). A `DEFENDER` call descends on the defender's edge of
the station, takes `MissionStance.DEFEND` whatever the company's stance is, and is otherwise
the company's ship: director, doctrine, brawler flux, 90 s on station, departs. Nothing
about the ship differs; only who supplies it and who decides when.

**Who gets one.** `HostileShipSupport.sourceFor(TargetProfile)` at launch (`MissionLaunch`):
a world whose `defenseLevel` is at least 4 (about 0 to 7; a battle station with ground
defences or heavy batteries clears it) fields a frozen stock hull by owning faction:
Tri-Tachyon and Persean League send a Paragon, every other owner an Onslaught, and pirates
and the Luddic Path send none. The company's own `stationedStrength` is not counted (it is
the company's contribution to the colony, not the world's). No ship for a stationed
company's colony, a Corvus chapter (it scripts its own), or a mission with no named defender
(supply routes, the opening operations, silent colony, civilian rescue).

**When.** A battle-level observer, `HostileShipSupport`, ticked next to the other mission
observers, rather than a hook in each mission's command strategy: strategies plan squad
assignments from frozen frames and have no ship seam, and the call needs nothing the
defender cannot see. Every second it looks for the first of: a marine within 30 cells of a
defender-held `mustHold` node, or 180 s since the first marine was on the ground. It calls
once, at the held node nearest the marines' centre of mass (the marines' centre of mass if
the map has no such node).

**Warning.** The decision raises the story beat `ship_support.hostile_inbound` (a danger
notice, "HOSTILE CAPITAL INBOUND", through `HostileShipCommsPresenter` on the comms feed) and
a ground ring labelled the same way over the station (`HostileShipMarkers`, drawn on the
objectives layer). The ship is called six seconds later and the ring stays up until it has
settled, so the player has the warning, the descent and the glide to move.

**Debug.** The briefing's hostile pick is the same call from a debug source, taken at the first
look instead of waiting for the trigger (a debug battle wants to see the ship), over the
default defender station when no marine is on the map yet. Manual mode and flux off are applied
to the fitted hull by `DebugWarship.applyPick`. `DebugWarship.install` remains only for the
older scenes that place a ship directly.

**Evidence.** `hostile-ship-support` (low-defence, high-defence, both-ships, strike) and unit
tests for source selection and the trigger. In the high-defence loop a company that wins its
control fight is wiped out once the ship is on station: a capital over a keep is the moment the
battle turns, as designed. With the company's own ship over the same ground the two do not
fight (ship guns do not take hulls as targets, G4), though ground ordnance landing under a hull
is absorbed by its shield as ever; strike craft go for the hostile hull and are shot down by its
flak (G3).

### Open questions (slice 4)

1. The thresholds are drafts: defence level 4, threat radius 30 cells, 180 s contact timeout,
   six seconds of warning. A live look should say whether six seconds is enough to react to.
2. *Decided (owner):* a defender's ship is meant to be that dangerous; it is a late-campaign
   threat, and a company that meets one without an answer should lose the ground. The
   counters are friendly forces (an employer's or ally's own ships and strike craft), the
   company's own capital in atmosphere, and keeping the enemy's ship bogged down elsewhere so
   it never comes (slice 6). Ground weapons do not become a counter; the balance law stands.
3. Should a lost hostile ship count for the battle (a morale or score beat)? Not built.
4. Mission types whose defender is not the market owner (relief, counterattack) take the same
   profile-based ship; whether some missions should opt out is undecided.

## Slice 5 as built

A low pass is an order a ship takes through one seam, `WarshipSystem.orderLowPass` (a script,
a scene, the debug panel; the pilot has `WarshipPilot.lowPass`): a straight line from A to B. A hull
not at A runs in to it first. Nothing about the ship changes, so the balance law stands: no damage
multiplier, no bonus, only different ground and a different clock.

**Flight.** The pass speed is the hull's own table speed times 6 (a burn: an Onslaught's 1.1 cells/s
hover becomes 6.8, a Paragon's 1.4 becomes 8.1), accelerating and braking at 6 times its own
handling so the burn is reached within the run (about 2.5 s). It flies the line without braking
and steers to a point 12 cells ahead on it, so it recovers from a crooked run-in. The line is at
least 24 and at most 140 cells (a longer request is cut from A), with both ends kept on the map. At B
the pass ends and the ordinary movement (station, route, or the pilot's hands) resumes from the speed the
hull has, shedding it at the burn's handling and then flying home at its hover speed. A hull that
finishes 140 cells out therefore takes about two minutes to be back on its station. The station
clock does not run while it is away, so that costs presence and not seconds; a pilot flies it
back sooner by hand.

**Fire.** A circle of 14 cells radius (the corridor's half-width) is laid on every battery the ship has,
centred on the line 8 cells ahead of the hull and sliding down it, so the guns work whatever is in the
corridor and nothing outside it. It is the pilot's laid-area machinery, so it is a hand order: no posture
range or friendly check refuses it, every mount still picks hostile units in the circle that it can
bear on from the pass's heading (along the line, so a broadside answers what lies abeam), and flux,
ordnance, arcs, spread and friendly fire stay the ship's. It does not saturate: it fires on units, not on
empty ground. Whatever a pilot had laid before is given back when the pass ends; the ship's director,
doctrine and hold-fire policy are untouched and resume. The pilot's hold-fire key (1) silences the pass's
guns for the rest of the run and leaves it flying.

**Cost.** Every second flown, the run-in included, is charged 2 seconds of station time
(`LowPass.STATION_COST_FACTOR`), and station time is otherwise counted only while stationary, so a pass is
what it costs and no more. A ship is not sent on a pass unless it has the whole estimated cost left (an
unbounded stay always can), and an exhausted stay never sends it away mid-run: it departs after the pass.
An Onslaught's 140-cell pass is about 22 s of flight and 44 s of its 90 s stay. Friendly fire stays on and the
pass does not refuse friendly-dirty ground: a show of force fires over friendlies and the blasts hurt whoever
they land on.

**Who orders it.** The pilot: **F** at the helm flies a pass from the hull to the cursor, on ground the player
has observed (the fog rule of a laid area); the strip shows a `[F] LOW PASS` card (ready, running in, passing,
or no time, with the station seconds left) and the help panel two rows. The tender's hands do not have it. A
script or mission: `WarshipSystem.orderLowPass(sim, ship, ax, ay, bx, by)`. The debug panel: **F5** flies a pass
from the ship to the cursor.

**Presentation** (read from the pass's own clocks and the line, nothing stored). A blend rises over 1.5 s and
fades over 4 s after the end. The shadow lies close and sharp under the skimming hull (its altitude falls to 45%
of the hover figure and its density rises 40%), the exhaust streak and two beam-edge vapour streams lengthen, and
dust is kicked along the line, fixed in the world and settling over 7 s, so the trail lingers behind the ship.
Sounds are the game's own: `engine_accelerate` low when it is sent and the deep `gate_explosion` report when it
comes onto the line, each once.

**Evidence.** `LowPassTest` and `WarshipLowPassTest` (line, speed, time-cost arithmetic, clocks and the order's
lifecycle), and the `ship-low-pass` scene and snapshot suite: a pass down a line beyond the director's reach
(12 of 12 down, 0 abeam, 21.8 s flown, 43.7 s charged), the director-only control (none), the pass over
friendlies (fires over them) and its control. The line sits beyond the longest gun's reach so the director
cannot be credited.

### Open questions (slice 5)

1. The numbers are drafts: speed x6, corridor 14 cells, cost x2, length 24 to 140. A live look should say whether
   a 140-cell pass is worth 44 s and whether the two-minute hover home wants a faster return.
2. A scripted or director-chosen pass (a ship that decides to pass on its own) is not built; only an order
   starts one.
3. Aircraft and ground fire against a passing ship are unchanged; whether a burn should make it harder to hit
   is open.
