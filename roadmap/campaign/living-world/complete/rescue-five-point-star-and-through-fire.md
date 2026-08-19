# Rescue five-point star and swarm through-fire — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `5f2c083d`

## Outcome

- Civilian-rescue pickup placement now requires an inward landing zone at a
  ten-cell edge inset on production-sized maps plus five distinct, reachable
  perimeter anchors arranged around it. Small test maps adapt the inset without
  restoring edge placement.
- The delayed defense line now fields five four-person militia squads, one at
  each star point. Every drop receives a deterministic randomized militia
  loadout from the standard SMG/DMR/pulse-rifle defender pool instead of the
  shuttle fallback weapon.
- Militia replacements begin below sixteen live-plus-inbound guards, restore up
  to four at a time without exceeding twenty, and reinforce the weakest star
  point. Pickup guards use a tight two-cell standoff so their assigned points
  remain legible.
- The seed-selected Bulwark/Sirocco unloads toward the formation center, then
  cycles all five squad positions while the line is quiet. Contact behavior can
  still take priority, and a lost pickup mech receives a replacement Valkyrie
  after the same twelve-second support cadence.
- Swarm placement excludes the full pickup formation footprint so the delayed
  defenders do not materialize inside an existing runner cluster.
- Alien durability is halved again: generic `ALIEN` health is 3.75 and
  `SWARM_RUNNER` health is 3, one eighth of their original pools.
- A missed direct-fire ray that physically crosses another hostile now uses a
  100% base incidental catch chance, still subject to that victim's incoming
  accuracy modifier. Each ray in a burst resolves independently, so wide fire
  can transfer misses into other bodies in a dense swarm. Friendly incidental
  catches retain the existing 35% base chance, muzzle-distance ramp, and half
  damage.

## Verification

- Focused coverage locks the inset five-point placement, five unique militia
  assignments, randomized standard loadouts, weakest-point replacement,
  pickup-mech replacement and patrol, eighth-health values, and hostile
  through-fire behavior.
- `gradlew.bat test` passes 1,786 root tests plus the asset-pipeline test
  (1,787 total).

## Manual follow-up

Play LOW/MEDIUM/HIGH rescue missions to judge whether twenty delayed militia
and the replacement mech preserve the intended unlimited-swarm pressure,
whether the seven-cell star reads cleanly around terrain and parked craft, and
whether burst through-fire is strong enough without making narrow lanes feel
automatic.
