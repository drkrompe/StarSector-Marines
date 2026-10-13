# Ship support — ships in atmosphere for every mission

Status: ACTIVE

Written: 2026-10-13

Updated: 2026-10-13 — slice 3 (arrival and departure in atmosphere) is built; the planet tint is a seam, the hostile ship and low pass remain.

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
| Writeback | hull damage and readiness to the member (slice 2) | none |

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
- **Activation.** The ship is fitted from the frozen loadout, enters from the company's
  own map edge, flies to a station near the cell, and works it under its side's
  `MissionStance` (an attacking company's ship supports the assault; a defending
  company's denies the drop). A tender builds there by the same stance director.
- **Pacing.** One activation per source per battle, a bounded station time
  (`stayFor`), then it departs back the way it came. The player can take the helm
  through direct control at any point, as today.
- **Loss.** A ship taken to nothing is lost for the battle, and (slice 2) the campaign
  member takes the damage. That risk is the honest price of bringing a capital, and the
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
2. **Writeback:** hull damage and a readiness cost to the committed member, through
   mission settlement. Unlocks the deferred readiness item in `warship-on-station.md`.
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
   unchanged. What remains: the planet tint is a seam only (`WarshipRenderSystem.surface`;
   the battle does not yet carry the target's palette to presentation), the power must call
   `arriveFromAbove()` on activation (the debug install does), and a departing ship is
   hidden by its climb well before it leaves the map. Needs a live look for how the
   cloud, glow and dust read over real terrain and under the live camera, and for the
   audio levels.
4. **Hostile ship support:** the defender's ship on the same mechanism, offered by the
   target profile, with a warning beat.
5. **Low pass.**

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
debug pick stays a launch-time install until slice 4. Activation needs a cell the player
currently sees, fits the next source at the company's edge (`ShipStationing`), stations it
on the cell, takes the stance from the company's side (ATTACK, or DEFEND for a stationed
detachment), turns on the director and doctrine, and bounds its visit at 90 s for every
hull. Drafted owner answers applied: cruisers, capitals and the Shepherd; 90 s; no
writeback.

Finding: a capital's own hover speed makes the approach long. An Onslaught entering at the
west edge took about 74 s to reach a cell 80 cells in, so most of a 90 s visit is travel
for deep targets. Tuning (a faster arrival, station time counted from entry, or a nearer
entry point) is an owner call alongside slice 3.
