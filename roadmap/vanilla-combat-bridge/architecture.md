# Vanilla Combat Bridge — Architecture (post-S2 north star)

> Written after S0/S0b/S2 proved the mechanism (launch + spectator canvas + native AI
> engaging sim-slaved proxies). This is the design north star for the **S3 phase** —
> the actual two-engine coexistence (fleet fight *above*, ground battle *below*, with
> cross-interaction). Design only; no code commitments beyond the decisions called out.

## Core principle: the adapter is the blast radius

`combathybrid` is the **only** package that imports `CombatEngineAPI` / `ShipAPI` /
`EveryFrameCombatPlugin`. `battle/` (the headless sim) imports zero combat API and must
keep it that way. Every version-fragile call lives in the adapter, so a Starsector
update can only break *one* package, fixable in one place.

**Enumerated fragile surface** (keep this list short — it's the whole maintenance cost):
`getFleetManager().spawnShipOrWing`, `getLocation()/getVelocity().set`,
`setExtraAlphaMult`, `setCollisionClass`, `removeEntity`, `addLayeredRenderingPlugin`,
`addPlugin`, `getViewport().set/setExternalControl`, a damage read (listener or HP
poll), `setDoNotEndCombat/endCombat`, `setPlayerShipExternal`. ~12 calls today.

## Decision 1 — coupling is event-translated, not state-mirrored

Reduce coupling to the minimum: **duplicate nothing mutable.**

- **The sim is the single source of truth** for all ground HP/state.
- **The proxy's vanilla HP is a throwaway "hittable surface,"** not a mirror of sim HP.
  Set it to a large constant so vanilla never decides the entity is dead — the *sim*
  decides death. The proxy exists only so vanilla weapons have something to hit and
  fighters perceive a target.
- **Two one-way event flows through the adapter:**
  - **Vanilla → sim (damage):** proxy reports *damage dealt* (a delta, via a vanilla
    damage listener or HP-poll); adapter forwards to
    `BattleSimulation.applyExternalDamage(Unit, float)` (exists today — built for flyby
    strafing; routes through `DamageResolver`, morale/fallback short-circuited).
  - **Sim → vanilla (lifecycle):** unit died → despawn proxy; unit moved → reposition
    proxy; structure destroyed → despawn. Rides the sim-side **pub/sub death/spawn
    mailbox** ([[battle_death_dispatcher_design]]): `battle/` *publishes* to a sim-side
    listener interface, `combathybrid` *subscribes*. `battle/` never learns the adapter
    exists — the one-way dependency holds.
- **No per-frame HP reconciliation.** The only shared vocabulary is `damage:float`,
  `entityGone:id`, and `position`. That's as decoupled as two engines get.

Why: minimizes the fragile surface and the conceptual coupling, so the adapter survives
infrequent Starsector updates with adapter-only maintenance.

## Decision 2 — proxies are an aggregation layer, not 1:1 with sim entities

Targetability tiers map directly to "what gets a proxy":

| Sim thing | Proxy? | How it takes damage |
|---|---|---|
| Infantry / soldiers | **No** | Area only — never directly targetable from orbit |
| Squads (clusters) | Optional area-proxy | Area; or untargeted, splash via nearby structure fights |
| Defenses / AA / turrets | **Yes**, one each | Direct — destroyable by fleet/fighters |
| Compounds / structures | **Yes**, one each | Direct |

**Area damage model:** vanilla area effects (fighter strafing runs, ship main-battery
fire support) deal damage at a *point*; the adapter translates that to
`applyExternalDamage` over a *radius* of sim units (infantry included). So infantry die
to splash, never to a lock-on. This is the [[spatial_unit_index]] `gather()` radius
query feeding the damage push.

## Spatial model — "above" vs "below"

Vanilla combat is strictly 2D; there is no altitude. "Fleet above / ground below" is a
spatial *convention* (a band of the map). 

**OPEN FORK (decide at S3c):** is the ground band a **hard** region that ship AI is
gated out of (ships physically can't/won't enter it — needs real AI work), or a **loose
convention** where carriers simply hang back and *fighters* do the air-to-ground (ground
proxies sit in a band ships have no reason to enter)? The loose version is far cheaper
and may be sufficient; the hard version is the "wards away from planetary defenses until
the fleet or ground forces break them" fantasy in full. S3c de-risks this; don't pre-commit.

## Decomposition — the S3 phase

Each is a probe in the S0–S2 spirit: smallest thing that answers the load-bearing unknown.

- **S3a — sim coupling slice** *(next build).* One real squad/turret `Unit` behind a
  proxy: HP-drain via `applyExternalDamage`, death event despawns the proxy. Proves
  Decision 1 end-to-end with a single live entity. → `stories/s3a-sim-coupling-slice.md`
- **S3b — cityscape backdrop.** Point the existing ground renderer at the combat world
  transform on the below-ships layer (instead of its FBO dialog target). Terrain +
  structures + scarring only (infantry stay sim-internal). → `stories/s3b-cityscape-backdrop.md`
- **S3c — airspace banding / AI gating** *(the hard de-risk).* Resolve the spatial fork;
  if hard banding, influence ship AI to hold the fleet band and skirt planetary defenses.
  → `stories/s3c-airspace-gating.md`
- **S3d — shuttle scale-down handoff.** A shuttle (`AirBody`) descends and "lands"; a
  camera/LOD transition between fleet scale and ground scale, plus the gameplay handoff
  (troops leave the fleet layer, appear in the sim). → `stories/s3d-shuttle-scaledown.md`

Sequencing: **S3a first** (it's the real coupling and unlocks everything), then S3b
(makes it legible), then S3c/S3d in whichever order the playtest of S3a+S3b suggests.

## Invariants to hold across the whole phase

1. `battle/` never imports `combathybrid`. Sim → adapter is pub/sub via a sim-side interface.
2. Sim is authoritative for ground state; vanilla HP is disposable.
3. Proxies aggregate; infantry are never directly targetable.
4. All version-fragile calls stay inside `combathybrid` (keep the enumerated list honest).
