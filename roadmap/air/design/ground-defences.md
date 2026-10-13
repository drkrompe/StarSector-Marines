# Ground defences — fortresses that hold the sky

Status: DRAFT

Written: 2026-10-14

Read `air-nouns.md` (**Overhead ships**, the balance law), `warship-behaviours.md` (gap
G5, the declared capital hazard and standoff), `ship-support.md`, and the two
command-power stories `shielded-fire-support-zones.md` and
`orbital-battery-fire-support-lock.md` first. This doc proposes what a world's ground
defences are in a battle. Nothing here is built.

## What the owner asked for

"Larger fortress-like standing structures with batteries and flux shielding, to be a
barrier to a ship simply flying through." Today nothing on the ground can hurt a ship (an
ordinary turret's round glances off the armour rule), so a capital over a battle goes
wherever it likes. The balance law already anticipated this: ground-to-ship is weak "at
least until planetary defences are built".

## The vanilla fiction is three different things

The market's own industries name them, and each does a different job:

| Industry | Vanilla text, in short | In battle |
| --- | --- | --- |
| Ground Defenses | a dispersed network of small, hidden weapon installations | **Defence nests**: many small hardened anti-air and anti-infantry emplacements. A threat to aircraft and people, not to a capital. Largely what defence posts already are. |
| Heavy Batteries | armoured subsurface batteries; no captain would get within their firing envelopes | **Fortress battery**: a few large structures carrying ship-grade guns whose envelope a ship must respect. The barrier. |
| Planetary Shield | a plasma envelope that disrupts long-range bombardment | **Shield generator**: a structure projecting a dome with its own flux pool that soaks ship-grade ordnance landing inside it until it overloads. |

So the target profile needs three signals rather than one `defenseLevel` number: whether
the market has ground defences, heavy batteries, and a planetary shield. The two
command-power stories already ask for the battery and shield signals; this is the same
boundary change, made once.

## Fortress battery

- **What it is.** A standing structure several cells across, built into the fortress
  ward (or an installation's own compound), with a battery of one to three large guns.
  The guns are vanilla weapons with vanilla numbers, taken from what the game's own
  station modules mount (Hellbore, Mjolnir, Gauss, Heavy Mauler and the like by faction
  and tech level), so a battery fights a ship at **ship-vs-ship balance**, not at the
  weak ground-to-ship rate. Its hull and armour come from the same source (station
  armour is in the 1600 to 2400 range), so a battery is as hard to crack as a ship.
- **The envelope.** Each battery has a firing envelope: its longest gun's reach at
  ground scale. A hostile ship inside it is engaged; its rounds reach the hull through
  the existing detonation route (shield first, then armour), so no new damage rule is
  needed, only the ground-side aim at an airborne hull.
- **A barrier, by behaviour.** A ship treats a live envelope as a capital hazard: the
  existing standoff keeps its station outside, the director does not pick stations
  inside, a pilot sees the ring, and a low pass through one is a gamble the pilot takes
  knowingly. A ship can still fight a battery from inside its envelope; it simply pays
  for it.
- **Fire control.** Each battery has a fire-control room inside the structure. Marines
  who take or destroy it silence the battery (the capture, contest, recapture and
  destroy rules of `orbital-battery-fire-support-lock.md`). A silenced battery also
  releases the Orbital Barrage lock, so the two stories share one structure.

## Shield generator

- **What it is.** A structure that projects a dome of a fixed radius, with a flux pool
  and dissipation like a ship's shield. Hostile ship-grade ordnance and orbital
  barrage landing inside the dome is absorbed as flux instead of detonating. When the
  pool fills, the dome drops and overloads for a few seconds, then comes back.
- **What it does not stop.** People, small arms, ordinary ground weapons, shuttles and
  fighters pass through, as vanilla says it disrupts bombardment rather than sealing the
  ground. Infantry fight under it normally.
- **How it is beaten.** Two ways, and both are the point: concentrate enough ship fire
  to overload it and land rounds in the gap, or send marines to take or wreck the
  generator (the relay of `shielded-fire-support-zones.md` is this structure).
- **Vanilla figures.** Vanilla gives the planetary shield no combat numbers, so the
  dome's capacity and dissipation are authored, sized against a capital's sustained
  output so that one ship alone overloads it slowly and two ships quickly. This is the
  one place the law's "numbers from vanilla" has nothing to read.

## Defence nests

The dispersed Ground Defenses network is mostly today's defence posts and overwatch
towers, with one change: a nest's flak and anti-air weapons engage aircraft (they already
drain aircraft through the air-threat field). They do not threaten a capital, keeping
ordinary ground-to-ship weak. Nothing new is required beyond the profile signal deciding
how many there are.

## What this does to a battle

The fun is a combined-arms loop that the battle does not have yet:

1. A defended world's fortress denies the sky over its keep: batteries keep the company's
   ship at standoff, and the shield soaks what it lobs in from outside.
2. The marines' job becomes opening the sky: take a fire-control room, wreck a
   generator, hold the ground long enough for the ship to come in.
3. The ship's job is to cover that assault from outside the envelopes, then move in
   once they are down, and finish what its guns are for.

It also gives the hostile capital's counters a ground half: a company defending a world
with its own batteries has an answer to an enemy ship that is not another ship.

## Slices

1. **Profile signals**: ground defences, heavy batteries and planetary shield on the
   target profile, read once from the market (shared with both command-power stories).
2. **Multi-cell structures**: the turret catalog takes footprints larger than one cell
   (today every structure is 1x1 by validation), with art for a battery and a generator.
3. **Fortress battery**: placement in the fortress ward, vanilla station-module guns,
   ground-to-air aim at a hull in the envelope, the envelope registered as a capital
   hazard, fire control and its capture rules, and the Orbital Barrage lock.
4. **Shield generator**: the dome, its pool and overload, absorption of ship-grade
   ordnance and barrage, the dome's look, and its capture rules.
5. **Defence nests**: the profile signal sizing the dispersed posts; anti-air already
   exists.

## Open questions for the owner

1. **Can a ship destroy a battery alone?** The draft says yes, slowly and at real cost,
   because the battery fights at ship balance. The alternative is that only marines can
   silence one, which makes the ground assault mandatory.
2. **Does the dome stop the company's own ship from firing out, or only hostile fire
   coming in?** The draft says it stops hostile fire coming in; its own side's ship and
   batteries fire out freely.
3. **Should a defending company ever get a battery or generator of its own** (relief,
   counterattack, garrison missions on a world it holds)? The draft says yes, through the
   same profile signal, which makes them a counter to hostile capitals.
4. **How rare are they?** Heavy Batteries and a Planetary Shield are late, expensive
   industries in vanilla, which matches hostile capitals being a late-campaign threat.
   The draft keeps the vanilla rarity rather than adding batteries to every defended
   world.
