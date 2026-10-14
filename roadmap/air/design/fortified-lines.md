# Fortified lines — the endgame map

Status: DRAFT

Written: 2026-10-14

Read `forts.md` first: what a fort is, its aspects, defence in depth and siting. This doc is
about what happens when there are many of them.

## What the owner asked for

Larger groupings of forts spread around a map, like star forts, with picket lines between
them protecting the objectives that matter. That is the endgame map feel: a world that has
been fortified on purpose, where the question is no longer how to take one fort but where to
break a line.

## Why a line and not more forts

A fort on its own is a puzzle with a location. A mission with five of them scattered across a
city is five puzzles. What makes a position feel defended is that its parts **cover each
other**: a gap between two forts is a gap both of them shoot into, and taking one fort
exposes the flank of the next. Without that relationship more forts is only more work.

## The pieces

- **Strongpoint.** A fort from the existing family (battery, shield, redoubt, citadel) acting
  as a node of a line. Nothing about the fort itself changes; it gains neighbours.
- **Picket line.** The ground between neighbouring strongpoints: trench, sandbag and wire
  with nests at intervals, the outer belt of `forts.md` run out from one fort to the next. It
  is fighting positions manned from the same defender pools, so a line is held where it is
  pressed and thin elsewhere.
- **Covered gap.** The stretch of picket line inside the fire envelope of the strongpoints at
  both ends. A line is laid so every stretch is a covered gap; a stretch only one end covers
  is a weak point, and a stretch neither covers is a hole.
- **Protected objective.** What the line exists for: the keep, a port, a generator, a
  shipyard. The line stands between it and where an attacker can arrive, and a mission says
  which objectives a line protects.
- **Star fort.** A strongpoint whose own trace is angled so its bastions flank the line
  either side of it. The angled bastion `forts.md` already uses is the seed; a star fort is
  that taken seriously at a larger scale, with outworks (ravelins) in front of its gates.

## Laws

- **The mission declares; map generation sites.** A mission declares a line (which
  objectives, roughly how many strongpoints, of what kinds); siting lays it across the
  ground it is given. Map generation never decides that a map has a line.
- **Coverage is measured, not assumed.** A laid line knows which of its stretches are covered
  gaps, which are weak points and which are holes, from the real fire envelopes and sight
  over the real ground. That knowledge belongs to the defender's planning and to evidence; it
  is not handed to the attacker.
- **Fair knowledge.** An attacker learns a line the way it learns a fort: by seeing its works.
  A weak point is found, not shown.
- **Ground and air together.** Strongpoints carry the anti-air that makes a sky full of
  fighters and boats costly (`forts.md`, optional anti-air aspect), so a line shapes where landings and air
  sorties can go as well as where infantry can walk. Breaking a strongpoint opens air as
  well as ground.
- **The phased assault is the attacker's answer.** Foothold, muster and planned assault
  (`phased-assault.md`) scale up: a company picks a weak point or a strongpoint, and the plan
  for a strongpoint is its stamp's plan. Nothing parallel is invented for lines.

## Open questions (for when this becomes stories)

- How a line is laid on a city: along existing roads and districts, or as a cleared belt the
  city is built around.
- Whether strongpoints share anything (power, fire control, reserves) or only fire. `forts.md`
  defaults to local power; a line may be where that default is revisited.
- How the defending commander reinforces a line under pressure, and whether a broken line
  falls back to a second one.
- Map size and performance: a line multiplies fighting positions and nests, and Conquest is
  already the slow map.

## Prerequisites

Redoubt and citadel (`forts.md` slice 4) and an evidence ground for a line in the style of the
trial ground (`conquest-trial-ground.md`) before any campaign map carries one. The AA aspect
and ships-against-forts evidence are built (`forts.md`, scenes `fort-air-defence` and `ship-vs-fort`).
