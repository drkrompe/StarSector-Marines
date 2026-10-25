# Vehicles rearm in battle

Status: PLANNED — the owner wants vehicles to rearm (2026-10-25); which of the options below, or which mix, is the owner's pick. The recommendation is C, then B.

Written: 2026-10-25

The model is in `convoy-nouns.md` (an empty magazine; the light roles; the laser
tanks) and `mechs-nouns.md` (the missile replenisher; salvo chains). The supply
side is `ResupplySystem` and `allied-tools.md` (what a truck rearms). The
mech/vehicle trade is `heavy-equipment-progression.md` (Vehicles: variants and
kits).

## Why

The owner noticed that vehicle ammunition never comes back. The laser tank and
the LRM carrier fire until their magazines are empty and are then out of the
fight for the rest of the battle. That is the model as written, not a bug: a
`GroundTurret` is seeded from its mount's `ammoCapacity` at spawn (times a ready
rack's factor), every round spends one, and nothing in battle adds any back.
`mechs-nouns.md` states it as an owner decision of 2026-10-23: "a vehicle magazine
is its whole stock and is never replenished in battle". The owner now wants that
reversed.

Mechs are not affected. Their arm guns, cannons and lasers alike, have no magazine
(`ammoCapacity` -1) and are bounded only by chassis flux. Their missile racks hold
a few ready salvos that the fitted loader refills one at a time for the whole
battle (standard 12 s for an SRM salvo and 18 s for an LRM salvo; accelerated
9 s / 13.5 s). A mech without a loader keeps what it launched with. The resupply
authority also feeds mech racks, one round per transfer.

So today the same weapon class has opposite rules on the two kinds of machine:

| Weapon | On a mech | On a vehicle |
| --- | --- | --- |
| Cannon, autocannon | Unlimited, flux-bound | Tank twin cannon: 48 rounds |
| Laser | Unlimited, flux-bound | Burst laser 600, PD laser 2000, *and* flux-bound |
| Missile rack | A few ready salvos, refilled by the loader | LRM carrier 60 missiles, SRM technical 32, never refilled |

How long a vehicle magazine lasts under sustained fire at the authored cadence,
ignoring aim time (rough figures from the mount and weapon data):

| Hull mount | Magazine | Lasts about |
| --- | --- | --- |
| LRM carrier (`mount.vehicle-lrm`) | 60 = 6 salvos of 10 | 55 s, less when chained |
| SRM technical (`mount.vehicle-srm`) | 32 = 8 salvos of 4 | 45 s |
| Hover scout pulse (`mount.hover-pulse`) | 160 = 40 bursts | 55 s |
| Heavy MG on the APCs (`mount.turret-heavy-mg`) | 200 = 40 bursts | 90 s |
| Tank twin cannon (`mount.tank-twin-cannon`) | 48 = 24 volleys | 2 min |
| Fire-support APC mortar (`mount.vehicle-mortar`) | 60 bombs | 2.5 min |
| Burst laser tank (`mount.tank-burst-laser`) | 600 | 3 min, with about a dozen overload vents |
| PD laser tank (`mount.tank-pd-laser`) | 2000 | 5 min |

A long battle outlasts every one of these. The missile carriers run dry soonest,
which matches what the owner saw.

## The options

### A. An onboard loader, as a mech has

Every armed mount may author a **replenishment cadence**: the seconds to restore
one trigger pack (a burst, a salvo, a bomb) to its ready magazine, repeated for
the whole battle, up to the mount's capacity. It is the mech replenisher's law
moved onto `GroundTurret` and each `MountedGun`: capacity limits the ready
rounds, not how many reloads there are in a battle. As a starting point the
missile mounts take the mech standard loader's cadences (an LRM salvo per 18 s,
an SRM salvo per 12 s), and the guns are tuned with `armorMatchup` and
`loadoutMatchup` against a control.

- **For:** it is what "replenish over time" literally means; no logistics, so it
  works on every map and in every mission; it is mostly a few fields and one tick
  pass.
- **Against:** it wears away the mech/vehicle trade
  (`heavy-equipment-progression.md`). A mech pays for its loader with a
  compartment socket and mass, and a vehicle would get one free. A vehicle could
  hold a position and fire forever, so a missile carrier is a free artillery
  battery for the whole battle. The three-salvo cap on vehicle crews loses its
  reason ("a vehicle magazine is its whole stock") and should return to the
  mech's whole-load choice.
- **A variant, A′: the loader as a kit.** An **autoloader kit** in the kit rack
  sets the hull's cadence from infinite to a number at the drop. That fits the kit
  law ("a number changed at the drop and nothing else") provided the
  replenishment law lives on the mount, so the kit only supplies its number. It
  keeps the trade: refilling over time costs a rack slot, which competes with
  plate, liner, skirts and the ready rack.

### B. The resupply authority serves hulls

A friendly cache already refills infantry special weapons, belt items and mech
racks one unit at a time (`ResupplySystem`). The cache can be an air drop or the
supply pickup's bed. B makes an armed hull one more recipient on the same rules:
a halted hull within the cache's service reach takes one trigger pack per
transfer from the cache's finite stock, and service pauses while a hostile stands
over the cache. The main turret is served first, then the secondary mounts. This
stays inside the convoy law that a supply hull "reaches munitions through the
battle's resupply authority" and "never a second supply rule".

What it needs:

1. **Hulls in the recipient walk.** `ResupplySystem` walks the dense roster, and
   a hull has no dense-roster slot, so it needs a second pass over live,
   unwrecked, halted hulls (`ConvoyService`). The contest check has the same gap:
   a hostile *hull* parked on a cache does not contest it today, and should.
2. **What one stock unit buys.** One trigger pack is the natural unit, matching
   one mech rack round. Whether a ten-missile salvo should cost a cache the same
   as one marine rocket is an open question (below).
3. **A dry hull goes to get rounds.** An autonomous deployed hull with an empty
   magazine and no order drives to the nearest friendly cache or halted supply
   pickup that is serving. It takes the same kind of route a disarmed withdrawal
   takes, waits there until full or until the stock runs out, and then hands back
   to engagement. Today it holds where it is and still reads as a threat. With no
   cache on the field it keeps today's behaviour.
4. **The player can do it by hand.** A move order beside a halted supply pickup
   is enough; the direct-control HUD already shows the magazine.
5. **Optional, later: mission trucks.** A hull posted at a place could join that
   place's `GunStock`, so a mission's supply truck (`allied-tools.md`, what a
   truck rearms) refills it on arrival like the place's guns.

- **For:** keeps the trade: a mech supplies itself, and a vehicle is cheap and
  sturdy but needs a supply line. It gives the supply pickup a reason to drive
  with an armor column, and the emergency-resupply command power a second use.
  One supply rule serves everyone.
- **Against:** the most work (a recipient walk, pricing, a seek-supply behaviour
  for the AI), and it does nothing in a battle with no cache or supply pickup,
  which includes every enemy and allied hull the owner did not equip with one.

### C. Lasers carry no magazine

A mount that authors flux may also author **no magazine** (`ammoCapacity` -1,
the mech convention), and the two tank lasers do. Flux is already what limits a
laser: the burst laser overloads inside a charge-up and the PD laser after a
long, held fight (`convoy-nouns.md`, the laser tanks). That is how a mech's
lasers work and how the space game treats energy weapons. Law: **a mount with no
magazine must author flux**, so something always bounds it.

What it touches: a `hasAmmo` / spend seam on `GroundTurret` in place of the raw
`ammo` gates in `GroundVehicleTurretSystem`, `VehicleEngagement` and the HUD;
`Armament` reporting unlimited triggers; the spawn path not multiplying a
missing magazine by the ready rack; `VehicleKit.READY_RACK` no longer suiting a
hull whose only mount has no magazine; and the fabrication bay's held
`frameAmmo`. The emplacement lasers (`mount.turret-pd-laser`,
`mount.turret-burst-laser`, `mount.hardpoint-laser`) author no flux and keep
their magazines unless the owner says otherwise. The hover scout's pulse emitter
is an energy weapon with no flux; it keeps its magazine unless it gains flux too.

- **For:** small and self-contained. It fixes the laser tank directly, and it
  makes a vehicle laser obey the same rule as a mech laser.
- **Against:** it fixes only the lasers; the LRM carrier, the tanks' cannon, the
  MGs and the mortar still run dry. A laser tank that never runs out is stronger
  in a long fight, and flux becomes its only limit, so `armorMatchup` and the
  `tank-versus-infantry` / `tank-versus-air` scenes should be read again.

## Recommendation

**C first, then B; A′ as the fallback.** C is small, removes the clearest
contradiction (a laser that runs out on a tank and not on a mech), and needs no
new behaviour. B gives every ammunition-fed hull a way back into the fight while
keeping the split that makes a vehicle a different choice from a mech: cheap and
sturdy, but tied to a supply line. If B's playtest shows hulls standing dry in
missions that field no cache, add the A′ autoloader kit rather than giving every
hull a free loader. Plain A is the simplest to build, but it gives away the trade.

Meanwhile, until the owner picks, the work builds on this recommendation.

## Open questions for the owner

- Which option, or mix: A, A′, B, C?
- Under B, does a hull draw a cache down faster than a rifleman (a stock unit per
  pack, or a heavier price for a salvo or a cannon volley)?
- Under B, should a dry autonomous hull leave its post to rearm, or only rearm
  when the player sends it?
- Under C, do the emplacement lasers lose their magazines too?
- Under A or A′, does the vehicle crew's three-salvo chain cap go back to the
  mech's whole-load choice?

## Slices (for the recommendation)

1. **C — magazine-free lasers.** The mount law and its validation (no magazine
   needs flux), the `GroundTurret` ammunition seam, `Armament`, the HUD,
   ready-rack suitability and the two tank laser mounts. Unit tests on the seam
   and the validation; read the `tank-versus-infantry` and `tank-versus-air`
   scenes again.
2. **B1 — hulls as resupply recipients.** The hull recipient pass, the
   hull-contested cache, pack-per-transfer pricing. Acceptance extends the
   `supply-truck` scene: an LRM carrier that fired dry, halted beside the halted
   pickup, gets its salvos back while one out of reach does not, and nothing
   passes while either is moving.
3. **B2 — a dry hull seeks supply.** The autonomous seek-and-return behaviour,
   with a `battle.vehicle.*` control switch listed in `switches.md` and a scene in
   which a dry carrier drives to the pickup, refills and goes back into the fight.
4. **Later, if needed: A′.** The autoloader kit and the mount replenishment law.

When a slice ships, fold its standing content into `convoy-nouns.md` (the empty
magazine bullet and the light roles) and `mechs-nouns.md` (the vehicle salvo
chain sentence), and retire this story when the last one ships.

## Out of scope

- Campaign ammunition. Vehicles have no campaign wear; every drop starts full.
- The screened tank's active protection. Its interceptor magazine stays
  "mounted once", unless the owner folds it into B.
- Aircraft. Shuttles already refill every mount when a sortie cycles
  (`AirSystem`).
