# Skybattle / fleet control over the city (parked — future feature)

> The fleet fight that happens *above* the ground battle: the player's (and enemy's) ships
> contesting the airspace over the city while the ground sim plays out below. This is where
> ship-AI depth, fleet command, and the air⇄ground interaction economy actually matter.
> **Parked 2026-06-20** — not a probe-stage concern; revisit when the skybattle becomes a
> real feature rather than a backdrop full of strafing carriers.

## Why this is parked, not built

The S3c probe proved the *mechanism* (vanilla ships engage sim-slaved ground proxies
natively). Tuning the fleet's *behaviour* — making carriers commit, hold a sensible
standoff band, prioritise targets, and respond to the ground battle's state — is a feature,
not a probe. It only pays off once there's a real skybattle to control (player fleet vs.
enemy fleet over the city, command inputs, win/loss stakes), which doesn't exist yet.

## What we learned (carry-over from S3c lever 1)

`CarrierEngagementPlugin` (host/) issues a one-shot `ASSAULT` assignment to steer carriers
onto the ground band. **Playtest verdict: carriers commit briefly, then drift back** — the
side's admiral / ship-level caution reassigns them off the waypoint, and a one-shot order
doesn't stick. The lever ladder, in increasing grip (all confirmed available):

1. **`ASSAULT` waypoint assignment** (built) — drifts back; would need a re-issue loop when
   `getAssignmentFor` goes null, and even then fights the admiral.
2. **`ShipAIConfig.personalityOverride`** (`reckless`/`aggressive`) — reduces skittishness
   at the ship level; blunter, persistent.
3. **`setShipAI` takeover** — own the carrier's movement brain outright (a custom
   `ShipAIPlugin` holding a standoff band). The real answer if the fantasy is precise
   fleet positioning; also the S3d landing-takeover mechanism, so it arrives via that thread.
4. **`setFullAssault(true)`** on the task manager — persistent "all ships attack" mode;
   simplest but removes all caution (carriers may ram defenses).

## Scope when it wakes up

- Player fleet command over the skybattle (orders, target priority, hold-the-band).
- A standoff-band model so carriers project fighters without charging the defenses.
- Air⇄ground economy: the fleet breaking planetary defenses vs. ground forces breaking
  through, and how each side's progress changes the other's pressure.
- Enemy fleet presence (today only the player-side carriers exist over a passive ground band).

## Pointers
- `CarrierEngagementPlugin`, `CombatBridgeSession.enterEngine` — the existing wiring.
- [`s3c-airspace-gating.md`](s3c-airspace-gating.md) — the probe that surfaced this.
- [[combat_assignment_target_types]] — assignment-type ↔ target-kind rules.
