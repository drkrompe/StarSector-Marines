# 19 — Threat-Scored Engagement Leash (commit-vs-press, with auto-release)

**Design — partially down-paid.** Shots-of-opportunity during the zone-push
advance shipped (`8d33ca5`); the scored commit-vs-press decision and its
auto-release are designed here, not yet built. Replaces the binary
`haltOnContact` gate in the zone-push action family with a per-tick
threat-scored leash that decides *how much* to commit to a contact versus
press the objective, and releases for free when the threat fades.

## The problem (observed)

SQ-107 (dump `squad_107.json.data`), a marine squad on a commander
`CLEAR_ZONE` assignment for zone 77, was crossing the massive outdoor zone 0
under `ClearAssignedZoneGoal` (MISSION) → plan `EnterZone[77]` → `ClearZone[77]`.
Seven of eight members had locked onto a single enemy ~30 cells out in/near the
destination zone (`d92`, `pathLen` 29–38) and were marching at it; only the
members whose pursuit target was inside the ~24-cell attack range were firing.
They strode past nearer threats without returning fire, reading as "walking
right at enemies and not reacting."

Two distinct gaps behind that:

1. **No return fire on the approach** (now fixed — `8d33ca5`). The opportunistic
   shot in `AbstractZoneAction.advanceIntoZone` only triggered on the member's
   single pursuit target; out-of-range pursuit → silent march.
   `closestEnemyInAttackRange` now lets a marching member shoot the nearest
   enemy it can actually hit, MOVING stance, without halting or retargeting.
2. **Contact is a binary gate** (this story). `EnterZone` advances with
   `haltOnContact = true`: an in-range+LoS pursuit target stops the member dead
   and accelerates the squad replan "to let an engagement-tier goal take over"
   (`AbstractZoneAction.java` Javadoc). But the MISSION zone-push goal stays
   relevant and re-emits the same plan via plan-stickiness — so engagement never
   wins, the halt just pauses one member, and there is no scored notion of
   *whether this contact is even worth stopping for*.

## Why not "let ENGAGEMENT preempt"

The obvious fix — drop the MISSION priority, or insert an engage sub-step — is
the wrong shape, for the reason the user named: it needs a **release**. A
goal-priority flip (or any latched "now engaging" flag) is sticky state that
something has to tear down. Get the teardown condition wrong and the squad
either never resumes the push (the SQ-87 outdoor-clear freeze, story 17, in a
new skin) or thrashes between push and fight at the boundary.

And it's not a 0/1 question. A lone, half-strength, **retreating** militia squad
spotted off the route should not pull marines off-mission — keep advancing, take
shots of opportunity. A fresh fireteam dug into cover *astride the route* should.
That's a *scored* "does it make sense to keep fighting here, or press to the
objective" decision, gated into the **threat** concept.

## The precedent: `GuardPostPatrol.computeLeash`

This exact decision already exists in the repo — bolted to a static post instead
of a moving advance. `GuardPostPatrol.computeLeash`
(`infantry/GuardPostPatrol.java:229`) computes a local force ratio
(`TacticalScoring.countCombatantsWithin(enemy, …)` vs `(friend, …)` out to
`radius + SENSE_MARGIN`) and maps it to a continuous **leash radius**: the full
patrol box at even-or-better odds (fight forward to the perimeter against a lone
attacker), collapsing toward a tight `DEFENSIVE_RING` when outnumbered (give
ground to the strongpoint). It recomputes once per tick, leader-gated
(`cachedLeashRadius`), so there is **no latched state** — when the odds change,
the behavior changes for free.

That last property is the whole game. **The per-tick recompute is the release.**

## The design: generalize the leash from "post" to "advance axis"

Keep the modulation **inside the zone-push action family** (`EnterZone` /
`advanceIntoZone`), not as a competing goal. MISSION stays in control; there's
nothing to tear down.

```
threatScore(contact, squad, sim)  →  engageWeight ∈ [0 .. 1]
engageWeight                      →  off-axis leash + halt willingness
```

- **engageWeight ≈ 0** → keep marching the objective route; take shots of
  opportunity only. (Already shipped — `closestEnemyInAttackRange`.)
- **engageWeight high** → step off the direct route (up to a leash off the
  advance line), halt, and prosecute the contact.
- **threat fades** (neutralized / retreats / no longer astride the route) →
  weight drops → leash shrinks → squad resumes the push. No flag, no teardown.

Add **hysteresis**: commit above `X`, release below `Y < X`, so the squad
doesn't flip-flop at the threshold — the same damping role
`RETARGET_DISTANCE_MARGIN` (`TacticalScoring.java:627`) plays for target
stickiness.

### What feeds `threatScore`

The point is to score *threat*, not headcount — that's what makes the
retreating-militia case fall out correctly:

1. **Combat power, not headcount** — sum effective strength (HP × weapon
   suitability), so a half-strength squad reads low. `countCombatantsWithin`
   is the headcount stand-in; a power-weighted variant is the real input.
2. **Posture / retreating discount** — a contact moving *away* or disengaging
   is near-zero threat. The single biggest lever for the militia example:
   retreating → score collapses → keep advancing.
3. **Astride-the-route vs. flank** — a contact on the path to the objective
   scores high (it must be cleared anyway); one off to the side scores ~0 →
   shots of opportunity only. The geometric term that makes "press the
   objective" the default. Cheap read: distance from the contact to the
   squad's planned path polyline.
4. **Are they engaging us** — taking fire / casualties bumps the score (don't
   walk through a real ambush). `UNDER_FIRE_AT_LOS` already exists
   (`WorldStateBuilder.evalUnderFireAtLos`).
5. **Objective urgency** — distance/time pressure dampens the whole thing; a
   squad near its objective is stingier about deviating.

## Where "threat" should live

`THREAT_DENSITY_HIGH_AT_TARGET` is a `STUB_FALSE` placeholder today
(`WorldStateBuilder.java:68`) — the named-but-empty seam this work fills. The
honest long-term threat read is the **perception/influence layer**
([`15-perception-and-influence.md`](15-perception-and-influence.md)): the
`hostile_believed` channel, belief-gated so a squad can't react to enemies it
hasn't observed (and can't be flanked by accounting for forces it can't see).
`computeLeash` already carries the same perception debt, flagged inline:

```
// PERCEPTION-DEBT (story 15): omniscient enemy read; swap to
// squad.believedEnemies when Tier B belief ships.
int foes = scoring.countCombatantsWithin(enemy, anchorX, anchorY, sense);
```

This story inherits that debt and that fix path.

## Phasing

- **Cheap slice** (builds directly on `8d33ca5`): force-ratio + retreating
  discount + astride-route term via `countCombatantsWithin`, a leash off the
  advance axis in `advanceIntoZone`, hysteresis on commit/release. Omniscient
  threat read, debt-flagged like `computeLeash`. Self-contained inside the
  zone-push action; no goal-graph changes.
- **Full version**: swap the threat read to believed contacts / the
  `hostile_believed` influence field when story 15's Tier B lands; promote the
  power-weighted threat tally into a shared `TacticalScoring` primitive so the
  guard-post leash and the advance leash read the same threat field.

The cheap slice is independently shippable and directly removes the
"walking-at-enemies" frustration; the open question is whether to ship it on the
omniscient read or hold the whole thing behind the perception layer so threat is
honest from day one. (Leaning: ship cheap, debt-flagged — it matches the
existing `computeLeash` precedent and the perception swap is a localized later
change.)

## Code anchors

- `infantry/GuardPostPatrol.java:229` — `computeLeash`, the odds-scaled-leash
  precedent to generalize.
- `decision/goap/action/AbstractZoneAction.java` — `advanceIntoZone`; the
  `haltOnContact` binary gate to replace, and where `closestEnemyInAttackRange`
  (shipped) already lives.
- `decision/TacticalScoring.java` — `countCombatantsWithin` (force tally),
  `closestEnemyInAttackRange` (shots of opportunity, shipped),
  `RETARGET_DISTANCE_MARGIN` (hysteresis precedent).
- `decision/goap/world/WorldStateBuilder.java:68` — `THREAT_DENSITY_HIGH_AT_TARGET`
  stub seam; `evalUnderFireAtLos` (taking-fire input).

## Observable behavior (after the cheap slice)

- A squad on a `CLEAR_ZONE` assignment crossing open ground past a weak or
  retreating contact keeps advancing and shoots it in passing — no off-mission
  detour.
- A squad that walks into a real contact astride its route halts and fights,
  then resumes the push on its own once the contact is cleared or breaks — no
  latched engage state, no manual release.
- Debug dump: an `engageWeight` / `engageLeash` field per squad (or per member),
  recomputed per tick like `cachedLeashRadius`, surfaced via `SquadStateDumper`.

## Cross-references

- [Perception & influence](15-perception-and-influence.md) — the honest threat
  read (`hostile_believed`); shares the omniscient-tally perception debt.
- [Story bank](10-tactical-stories.md) — Story K (room-clear sweep) is the
  posture this leashes.
- [Garrison zone-clear scoping](17-garrison-zone-clear-scoping.md) — the
  SQ-87 outdoor-clear freeze is the sticky-state failure this design avoids;
  shares the `GuardPostPatrol` garrison vocabulary.
- `memory/feedback_scored_over_binary_gates.md` — the working preference this
  story encodes.
- `memory/battle_patrol_freeze_modes.md`, `memory/tier_override_design.md` —
  related squad-behavior failure modes and the priority-bucket model.

## Status

**Design — cheap slice ready to prototype, full version parked behind story 15.**
Shots-of-opportunity down-payment shipped (`8d33ca5`). Open decision: ship the
cheap slice on the omniscient threat read now, or hold for the perception layer.
