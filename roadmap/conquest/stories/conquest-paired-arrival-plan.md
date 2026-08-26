# Conquest paired arrival plan

Status: IN PROGRESS

Written: 2026-08-26

Read `conquest-nouns.md`, `air-nouns.md`, `company-view-nouns.md`,
`mapgen-nouns.md`, and `battle-fixtures-nouns.md` before implementing this
story.

## Intent

Make the Conquest beachhead arrive as coordinated squad echelons rather than
independent full-shuttle drops. Two transports share one authored landing area,
each carries six members of the same twelve-marine squad, and both depart after
unloading instead of following the squad as hovering fire support.

Establish the configuration boundary at the same time: a mission chooses its
arrival doctrine, the doctrine resolves the committed physical transports into
an immutable arrival plan, and Air executes that plan without turning a
Conquest choice into a global Valkyrie rule.

## Scope

- Preserve hull capacity as a physical maximum while allowing a sortie to
  embark an explicit smaller personnel count. Conquest Valkyries embark six.
- Author deterministic pair-capable landing areas in the Conquest BEACH band.
  Each selected area exposes two distinct clear shuttle berths and one logical
  assembly identity.
- Pair transports without crossing the employer/player ownership boundary.
  Both craft in a pair share launch timing, landing-area identity, and one
  squad-arrival group; later cycles create later squads.
- Load persistent personnel in paired-wave order so roster-adjacent seats
  `0..5` and `6..11` occupy the two craft in the same arrival group.
- Let tagged and generated personnel from both berths assemble into one battle
  squad while preserving the rule that different landing areas create labelled
  split fragments.
- Give each shuttle an explicit post-delivery disposition. Conquest uses
  drop-and-depart while ordinary armed transports retain bounded loiter.
- Capture embarked strength and arrival doctrine in the successor Conquest
  construction fixture without reinterpreting historical V1 fixtures.

## Acceptance

- [ ] Eight Conquest Valkyries resolve to four selected landing areas with two
  distinct, clear, footprint-spaced berths per area.
- [ ] Each Valkyrie unloads exactly six marines on every cycle, then enters
  `DEPARTING` without entering `HOVER_STATION`; after egress it still waits the
  ordinary rearm delay before its next sortie.
- [ ] Two paired craft unload one twelve-member squad with three stable
  four-member fire teams. Persistent identity, label, NCO, expected strength,
  and form-up all survive the split, with no `(A)`/`(B)` suffix.
- [ ] Generated fixture personnel also join one squad per area and cycle rather
  than minting one six-member squad per shuttle.
- [ ] The paired personnel load order is deterministic and cycle-major. An
  employer/player boundary starts a new arrival group rather than sharing one.
- [ ] Distinct landing areas retain the existing split-squad semantics, and a
  missing partner releases an understrength squad through the existing form-up
  timeout.
- [ ] Historical V1 fixtures replay their original full-capacity, independent,
  loiter-capable drops. New fixtures round-trip the resolved Conquest arrival
  inputs and the checked-in V2 workload reports 192 seats for eight Valkyries
  flying four six-seat sorties.
- [ ] Non-Conquest shuttle placement, payload size, staggering, and post-drop
  behavior remain unchanged.

## Constraints

- `ShuttleType.capacity` remains hull capability, not a promise that every
  sortie fills every seat.
- Landing areas and berths are authored spatial facts; mission setup selects
  from them and does not rediscover safe geometry from a single arbitrary cell.
- Fire-team membership remains roster-derived. A six-seat transport slice may
  temporarily divide a fire team in transit but may not rewrite its identity.
- Arrival grouping is battle-lifetime state. It must not create campaign squad
  identity for generated troops or persist a second company organization.
- Armed Conquest transports may fire while approaching, landed, or departing;
  drop-and-depart suppresses only the follow-squad hover phase.
- Arrival doctrine is tactical mission policy and remains independent of tier's
  total lift demand.

## Follow-ups

- Named personnel lost aboard a destroyed shuttle need explicit battle outcome
  writeback; this story must not fabricate survival, but may leave that broader
  transport-casualty settlement as a separately contracted slice.
- Player-selected forward landing areas, AA contesting, and alternate arrival
  means remain separate extensions once a second means has concrete gameplay.

## Exit

Fold the standing arrival doctrine, landing-area, embarked-load, squad-assembly,
and post-delivery laws into their canonical noun docs; add the story to
`shipped.md`; remove it from the board; and delete this file once every
acceptance item ships.
