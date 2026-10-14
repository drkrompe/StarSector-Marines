# Fortified lines — the endgame map

Status: DRAFT

Written: 2026-10-14

Updated: 2026-10-14 — owner: prepared positions, not obstacles (no literal razor wire, no mines); routes and funnels always exist; trench and fence lines are broken through by each unit's own abilities; line guns are felt out, not designed ahead.

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
- **Picket line.** The ground between neighbouring strongpoints: trench, sandbag and fence
  lines with nests at intervals, the outer belt of `forts.md` run out from one fort to the next. It
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
  fighters and boats costly (`forts.md`, AA aspect), so a line shapes where landings and air
  sorties can go as well as where infantry can walk. Breaking a strongpoint opens air as
  well as ground.
- **The phased assault is the attacker's answer.** Foothold, muster and planned assault
  (`phased-assault.md`) scale up: a company picks a weak point or a strongpoint, and the plan
  for a strongpoint is its stamp's plan. Nothing parallel is invented for lines.

## The feel: 1918 on a planet

The owner's touchstone is the Western Front in its last year, in a science-fiction setting: a
front that has been dug, fenced and concreted for years, where the defence is a system in
depth rather than a wall, and where an attack is a set-piece of preparation, assault,
consolidation and the next line. The proposals below take that literally where it helps.

## Proposed answers (owner leaning, not yet decided)

**The line replaces the city where it runs.** A line is not threaded through streets. Where a
mission declares one, the city is cleared from its ground: buildings are deleted, not
ruined into cover, and what is left is a belt of earthworks, fence lines, craters and concrete with
the city resuming behind and in front of it. The cleared belt is the line's own ground, laid
before the city grows into what remains, the same way a declared fort's footprint is
reserved today. This keeps the line legible (a trench system, not a maze of rooms), and it is
likely to make the map cheaper, not dearer: open earthworks are far fewer zones and walls
than the blocks they replace.

**A line is a system in depth, and a broken line falls back.** Three belts, outside in:

1. **Outpost zone.** Thin: listening posts, a few nests, and no-man's-land in front of it,
   cratered and broken. It warns and delays; it is not held to the last.
2. **Main line of resistance.** The strongpoints and the picket lines between them, with
   communication trenches running back. This is where the line is fought for.
3. **Support line.** Close in front of the protected objective, lighter, holding the reserve.

A stretch of the main line that falls is not the end of the battle. The defender either
retakes it with its reserve or, if it cannot, withdraws that sector to the support line. For
the attacker this is the phased assault repeated: foothold in the outpost zone, muster, break
a sector of the main line, consolidate, and do it again against the support line.

**Strongpoints share reserves and fire, not power.** Power stays local to a fort (`forts.md`),
because a single plant that drops a whole line's guns would make one fort the whole puzzle.
What a line shares is what the 1918 defence shared:

- **Fire.** A strongpoint's guns and nests cover the gaps either side, which is already what
  makes a covered gap.
- **A counterattack reserve.** Held behind the main line, out of the attacker's sight, and
  committed by the defending commander to a sector that has been broken into, along the
  communication trenches. It is the defender's half of the muster: a company formed in the
  rear and sent to a breach.
- **Warning.** An outpost that sees an assault forming tells the strongpoints behind it (fort
  command awareness, extended along the line), so a line is never surprised everywhere.

**Performance is a budget the evidence ground measures first.** Fighting positions are manned
only where pressed and nests are unmanned, which already keeps an idle line cheap. The open
costs are line-of-sight across long open belts and the number of nests and fighting-position cells. A line
evidence ground (in the style of the trial ground) measures tick cost alongside outcomes
before any campaign map carries one, and the cleared belt is expected to offset some of it.

## Proposed answers, second round (owner open to suggestions, not yet decided)

**The belt is measured in weapon reach, not cells.** No-man's-land is about as deep as a field
mortar's band, so an attacker's mortars cannot reach the main line from the city edge: they
have to be carried forward into the outpost zone first, which is exactly the foothold the
phased assault already starts with. The main line's own depth is a rifle's reach front to back,
so a fire step in the support trench can still shoot into a lost stretch of the main line.
The figures follow the weapons as they are tuned, rather than being fixed here.

**Three kinds of ground across the map.** In front of the line the city is **ruined, not
deleted**: roofless shells, rubble and broken streets, which is the attacker's approach and
the place a foothold and a muster happen. The belt itself is **cleared** as the previous
round says. Behind the line the city is **intact**: the rear, where the reserve waits and
the protected objective stands. A player reads the front at a glance from that gradient.

**No-man's-land is terrain with its own rules.**

- **Craters** are walkable heavy cover and fighting positions, so an attack can go to ground
  in them. Craters made during the battle (mortar, bomb, ship fire) join them, so the ground
  gets more broken the longer it is fought over.
- **Funnels, not walls.** There is no literal razor wire and there are no mines: the line
  should feel like prepared positions, not obstacles. Trench runs, fence lines and sandbag
  walls are angled so the open routes through the belt run into the lanes the nests cover,
  the way 1918 wire did. Routes through always exist; the map is never simply blocked off.
- **Break through, not round.** A trench or fence line is a thing an attack can break
  through where it chooses, rather than only circumvent. How is each unit's own abilities:
  whatever a unit already carries (charges, cutters, rockets, a mech's weight, a ship's or
  a mortar's shells) is how it opens a line, and no line-specific breaching tool is
  invented for it. A breach is a new route the defender then has to cover.
- **Dead ground**: low ground out of the line's sight, the attacker's way to close without
  being seen. Siting should leave some on purpose, so a weak point exists to be found.
- **Wrecks**: crashed boats, knocked-out mechs and vehicles from earlier fighting. Hard cover
  and landmarks; the science-fiction answer to the 1918 tank hulk.
- **Flooded holes and mud** slow movement without blocking it.

**The defender counterattacks first and withdraws when it can no longer win the sector.**

- **Counterattack** when a stretch is broken into and the reserve can reach it before the
  attacker has consolidated: soon, with the local reserve, while the attacker is still
  disorganised in the captured trench. That window is the defender's best chance.
- **Withdraw** the sector when the reserve is spent, when the strongpoint that covered the
  stretch has fallen (so the gap is no longer covered), or when the neighbouring sectors are
  outflanked. The neighbours bend back to face the gap rather than holding a line that is now
  behind them, and the sector falls back to the support line in order.
- An attacker that follows a withdrawal up quickly may catch the support line before it is
  manned; one that pauses to consolidate meets it ready. That is a real choice for the
  attacker and needs no special rule.

**Yes: a line has its own guns, and they are why air matters over a line.** Field guns and
mortar pits stand behind the main line, separate from the fort batteries, which fire at ships.

- They fire on **no-man's-land and the outpost zone**, on what the line's observers see (fair
  knowledge), from ground they have registered on beforehand.
- When an outpost signals an assault forming, they drop a **barrage curtain** across
  no-man's-land in front of it: the 1918 SOS barrage, and the reason an attack wants to cross
  quickly or under cover.
- They are the natural target of the attacker's **air strikes, strafes and counter-battery
  fire**, and the forts' anti-air is what protects them. That ties ground, air and forts into
  one fight: take the strongpoint's AA, then the air can find the guns.
- They use the same fire-mission machinery as the attacker's mortars, so the two sides are
  symmetric.

## Open questions (for when this becomes stories)

- A line's guns: a new emplacement kind probably looks better than crewed equipment in pits,
  but this is felt out in play on the evidence ground rather than designed ahead.
- Which existing abilities break which line features (a mech through a fence line, charges
  through a trench wall), settled when the features exist.

## Prerequisites

Redoubt and citadel (`forts.md` slice 4), the AA aspect, ships against forts as measured
evidence, and an evidence ground for a line in the style of the trial ground
(`conquest-trial-ground.md`) before any campaign map carries one.
