# Target-faction command doctrine

Status: PLANNED — follows the autonomous foundation and at least one paired non-Conquest command baseline; doctrine must bias competent legal choices rather than create them.

Written: 2026-08-24

Updated: 2026-08-25 — moved faction weighting behind the shared command foundation and measurable mission baselines.

Read `ai-nouns.md` and `campaign-battle-bridge-nouns.md` before implementing
this story.

## Problem

Target faction currently changes some campaign presentation and fighter pools,
but ground commanders share the same assignment and local posture policy. A
Hegemony garrison, Tri-Tachyon security force, Luddic Path cell, Diktat guard,
and pirate band therefore make the same tactical decisions once their generic
units reach the field.

Different stats or colors are not sufficient faction flavor. The commander
must express a recognizable preference while remaining bound by the same
mission objectives, beliefs, navigation, morale, and combat laws.

## Goal

Resolve one immutable **command doctrine profile** from the target faction id
at launch. The profile supplies bounded weights and thresholds to existing
legal decisions; it does not name Java classes or install faction-specific
planner scripts.

The first profile axes are:

- strongpoint commitment and must-hold preference;
- reserve concentration versus early commitment;
- recapture urgency after losing a compound or installation;
- ADVANCE/HOLD/DISENGAGE threshold bias within the existing hysteresis model;
- willingness to pursue beyond an assigned defensive place; and
- preference for concentrated versus distributed mission assignments.

No axis grants knowledge. Every decision consumes the same faction-local
beliefs, contact picture, influence snapshot, and authored objective context.

## Core-faction direction

| Faction | Command character |
| --- | --- |
| Hegemony | Deliberate layered defense, durable reserves, forceful organized recapture |
| Tri-Tachyon | Mobile security, selective commitment, displacement from bad local trades, precision around valuable sites |
| Persean League | Balanced line, distributed support, flexible reinforcement of threatened objectives |
| Luddic Church | Tenacious defense of populated, agricultural, and sacred places; limited pursuit away from them |
| Luddic Path | Aggressive closing, low ordinary disengage preference, urgent attacks on technological objectives |
| Sindrian Diktat | Rigid strongpoints, concentrated guard response, sharp counterattack after loss |
| Pirates | Opportunistic massing, ambush/disengage preference, weak attachment to ground without loot or escape value |
| Independents | Existing balanced behavior and fallback for unknown/modded factions |

These descriptions resolve to a small typed policy vocabulary. They do not
become eight bespoke commanders. Modded factions may map themselves to an
existing doctrine or provide a validated merged profile.

## Mission and installation context

- The mission owner still assigns what must be defended, captured, destroyed,
  or extracted. Doctrine only chooses among legal assignments and postures.
- A shield relay or orbital fire-control site may receive stronger protection
  because its authored mission context marks it important; doctrine must not
  discover installations from hidden map state.
- Conquest owns compound capture and reinforcement owns delivery. Doctrine may
  prioritize an exposed recapture target or reserve assignment but may not mint
  tickets, force a delivery means, or transfer ownership.
- Defender doctrine never applies to player squads. Employer militia in an
  authored operation uses the profile explicitly selected for that force; it
  does not inherit the target owner accidentally.

## Acceptance

- One deterministic scenario per core faction produces an inspectable
  difference in at least two doctrine axes while using identical visible facts,
  force scale, and objective layout.
- Unknown/modded factions preserve the current Independent/balanced behavior.
- The same faction profile is frozen at launch and survives every replan; AI
  never reads campaign factions during battle.
- Doctrine cannot expose unobserved enemies, bypass morale survival, leave a
  must-hold context illegally, change capture state, or create reinforcement.
- Live Conquest passes make Hegemony, Tri-Tachyon, Church, Path, Diktat, pirate,
  League, and Independent defenders recognizably different without one profile
  being a universal difficulty upgrade.
- Debug presentation names the active typed doctrine and the legal score/axis
  that influenced a choice; it does not reconstruct private beliefs for the
  player.

## Out of scope

- Unit composition and equipment — `target-faction-ground-rosters.md`.
- Contract generation and faction motive —
  `faction-ground-contract-policy.md`.
- Faction-exclusive goals, weapons, movement rules, morale immunity, or combat
  resolution.
- Strategic AI that changes campaign ownership or launches operations.
