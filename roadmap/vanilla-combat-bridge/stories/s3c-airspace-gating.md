# S3c — Airspace banding / AI gating

> The hard de-risk. Vanilla ship AI dogpiles all enemies in a flat 2D plane; the vision
> wants the fleet to fight *above* and skirt planetary defenses rather than charge into
> the ground band. **First lever built (assignment-based engagement); playtest pending.**

## Playtest finding (2026-06-20, Ctrl+Shift+K)

Unmodified AI does **not** behave acceptably, but not in the "dogpiles the band" way the
stub feared — the opposite. **Carriers idle at their spawn row and rarely commit.** The
root cause: vanilla ship AI advances onto a target only when an enemy fleet pushes
*toward* it; the ground proxies are stationary and never advance, and carriers are
skittish by design (they expect the enemy to come to them). So the fleet has no pull into
the band at all. The fork therefore resolves toward **steering the fleet in**, not gating
it out — and the cheapest lever is a vanilla assignment, not a `ShipAIPlugin`.

## ⏸ PARKED (2026-06-20) — moved to the skybattle feature

Playtest verdict on lever 1: **carriers commit briefly, then drift back** — a one-shot
`ASSAULT` order doesn't stick against the side's admiral/caution. Per the user, fleet-AI
depth is not a probe-stage concern; it belongs with the real **skybattle / fleet-control
feature** (the fleet fight over the city). The mechanism is proven (vanilla ships engage
sim proxies natively); the *behaviour tuning* is parked. The lever ladder and carry-over
live in [`skybattle-fleet-control.md`](skybattle-fleet-control.md). `CarrierEngagementPlugin`
stays wired (harmless one-shot ASSAULT) as the starting point for that story.

## Lever 1 — ASSAULT assignment at the ground band ✅ BUILT (parked; drifts back)

`CarrierEngagementPlugin` (host/): on the first frame a carrier is deployed, drop a
waypoint at the live targetable-entities' centroid (projected via
`GroundBattleConfig.cellToWorld`) and give every deployed PLAYER carrier an
`CombatAssignmentType.ASSAULT` assignment toward it (`useCommandPoint=false`, so the
spectator side's zero-CP budget is irrelevant). **`ASSAULT`, not `ENGAGE`:** ENGAGE
requires a specific enemy *entity* as its target and the engine throws `Invalid engage
target` if handed a waypoint; ASSAULT is the "move to this location and fight" order that
accepts a waypoint. It pulls the fleet to the band while leaving each ship its own
piloting — carriers advance to fighter standoff and let wings do the air-to-ground, rather
than ramming the defenses (which a blunt `setFullAssault` would risk). Issued once; if the
admiral reassigns ships off the waypoint, the fallback is to re-issue when
`getAssignmentFor` goes null (held out to read raw stickiness first).

**Next if Lever 1 is insufficient:** `ShipAIConfig.personalityOverride` bump, then a full
`setShipAI` takeover. The takeover is being built anyway as the **S3d landing/descent
foundation** (mid-combat AI swap is confirmed supported) — so the heavy lever arrives via
that thread regardless.

## Goal

Resolve the spatial fork from [`../architecture.md`](../architecture.md) and, if needed,
influence ship AI so the fleet holds an "above the surface" band and treats planetary
defenses as threats to ward away from — until the fleet destroys them or ground forces
break through.

## The fork to decide first

- **Loose convention (cheap):** ground proxies sit in a map band ships have no reason to
  enter; carriers hang back, *fighters* do the air-to-ground. May need zero AI work —
  test whether vanilla AI naturally leaves a band of stationary low-value targets alone
  while it fights the enemy fleet.
- **Hard banding (expensive):** ships are actively gated out of the ground band and
  steer around planetary-defense threat zones. Needs a custom `ShipAIPlugin` and/or
  combat assignments / threat fields.

Decide empirically: set up a fleet fight + a ground band of defense proxies and *watch*
what unmodified AI does. The result picks the path.

## Scope (once the fork is decided)

**In (hard-banding path):** custom `ShipAIPlugin` or assignment/threat steering that
keeps the fleet in its band and skirts defense proxies; verify it composes with the
proxy damage loop (ships still *can* destroy defenses, just don't suicide into them).

**Out:** real altitude (doesn't exist in vanilla — banding is always a convention);
campaign-fleet consequences; tuning the full "fleet vs ground forces race" economy.

## Open questions

- Does unmodified AI already behave acceptably against a band of stationary low-priority
  targets (making this story mostly a no-op)?
- If we override `ShipAIPlugin`, how much vanilla behavior must we reimplement vs. wrap?
- How do planetary defenses signal "threat" to ship AI — real weapons on the proxy
  (engine resolves it) vs. a synthetic threat field?

## Acceptance

In a fleet-vs-fleet fight over a defended ground band, ships fight the enemy fleet and
engage/avoid planetary defenses sensibly rather than ignoring the band or charging into
it. Verdict: which fork, and how much AI work the chosen one actually needs.
