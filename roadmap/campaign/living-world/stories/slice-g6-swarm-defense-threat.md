# Slice G6 — swarm-defense threat payload

**Status:** CONTRACT LOCKED (2026-08-12)

## Goal

Replace the civilian-rescue battle's temporary conventional defenders with the
held biological swarm content while preserving the completed evacuation and
campaign-writeback contract.

## Locked v1

- Keep `Faction.DEFENDER` as the opposed battle side. Add append-only
  `UnitType.SWARM_RUNNER` and `UnitRole.SWARM_PRESSURE` identities.
- Reuse held `alien.png` and `alien-dead.png`; leave legacy `ALIEN` intact.
- Runners are fast close-contact attackers with no weapons, equipment, squad
  GOAP, morale, ranged shots, conventional reinforcements, or fighter support.
- Target only registered active evacuees, then live marines. Never acquire an
  ambient civilian through a broad civilian-faction scan.
- Spawn a risk-scaled mission-local roster on complete reachable cells outside
  both the shelter and lift zones. Invalid placement aborts that map attempt.
- Replace the rescue factory's temporary Extraction roster; do not change
  evacuation scaling, zero-economy mission terms, or hidden moral effects.

## Delivery hunks

1. Add and test the append-only runner type/role contract.
2. Add controlled-fixture pressure behavior with explicit target priority and
   contact damage.
3. Add deterministic risk-scaled roster placement and install it in the rescue
   factory without conventional defender/reinforcement content.
4. Run the full automated build and record the payload checkpoint. Manual
   playtesting remains skipped for this session.
