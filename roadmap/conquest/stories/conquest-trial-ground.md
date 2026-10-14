# Conquest trial ground

Status: IN PROGRESS — mission layer extracted and trial ground playing; first finding: an unmanned fort is invisible to the defender commander

Written: 2026-10-14

Updated: 2026-10-14 — steps 1–3 built; reactive manning blindness fixed (token picket, fort-gun reports, threat memory). Home tracks follow work, stages stand until reached, step toward the track's compound and go round a fort that is not the objective: the objective falls in all 15 fixtures (9 offset, 6 on-path) and marines win 8, against none before. The 7 timeouts lose the keep: the default trial force (four 12-seat squads, rifles only) runs out of marines.

## Intent

Commander and garrison work (`reactive-garrisons.md`, the muster, structure
planning) is currently measured on the full 560x336 Conquest map, whose lanes and
city generation are unfinished. A swing there can come from the map as easily as
from the behaviour under test, and single runs move by hundreds of samples.

A trial ground is a deliberately small Conquest map with exactly the elements a
commander question needs, so attacker and defender composition and the fort's
position can be varied one at a time:

1. one marine landing zone at the low end of the axis;
2. one battery fort, built by the production fort stamp with its belt of
   trenches and sandbags, either **on the path** between the landing and the
   objective or **offset tangentially**, inside sighting range but off the line
   of advance — the second asks whether the marine commander engages a target
   that is not in its way;
3. one objective compound on the lane, plus the one keep Conquest's victory law
   requires, behind it.

## The layering this needs

Mission behaviour and map source are separate layers everywhere except Conquest.
Campaign maps (`NorthWeir`, `JunctionCompound`, ...) are code-authored recipes
producing a `MapResult`; each mission then populates the finished map. Conquest's
population (objectives, arrival, defender allocation, both commanders,
reinforcement) is fused to its city generator inside one build. This story
extracts it, unchanged, so it runs on any `MapResult`; the city path then becomes
"generate, then populate", byte-identical to before.

The trial map is then one more authored recipe in the campaign-map style, and
nothing about it is special to the mission layer.

## Constraints

- The extraction preserves the canonical Conquest replay byte for byte (same
  random draws in the same order).
- Defender composition is controllable through an explicit roster, not only
  through tier and risk; the derived roster stays the default.
- The trial is evidence, not a test: an opt-in `commanderEvidence` mission
  argument, excluded from `test`.
- Exactly one COMMAND_POST (the keep). The lane objective is a non-keep compound,
  or the marine commander starts in keep convergence and the lane is never
  advanced.

## Plan

1. Extract the Conquest population into a public seam over a `MapResult`, with
   an optional explicit defender roster. Verify the canonical matrix replays
   identically.
2. The trial map recipe: open ground, landing pads, the lane objective, the keep,
   and the fort pinned on the path or offset, with fighting positions present.
3. `commanderEvidence -Pmission=conquest-trial`: a small fixture matrix (fort on
   path / offset; attacker and defender sizes), reporting captures, fort contact,
   garrison manning and casualties.

## Acceptance

- The canonical Conquest traces are unchanged by the extraction.
- Both fort placements build, carry fighting positions, and play to a report
  under the production commanders.
- Fixture fields set attacker seats and defender roster without code changes.
