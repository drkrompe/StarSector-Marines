# 10 — Stage 2 Tactical Stories

**Stage 2 kickoff.** Before authoring the next action library, write down
the combats we want to see. The action set falls out of these stories — not
the other way around.

## Why a story bank

Stage 1 named actions from the F.E.A.R. rolodex (`Suppress`, `MoveToFlank`,
`TakeCover`, `AdvanceUnderCover`). That's a fine starting set in the
abstract, but it doesn't tell us what counts as "done" for any of them, or
which one matters first. A `Suppress` that produces no observable change in
the firefight is worthless code.

The story bank is the constraint layer. Each story names:
- A combat moment we want the player to *see* happen
- The observable signal that tells us it landed
- The minimum primitives the AI needs to play it

When we go to implement an action, the implementation target is "make
Story X visibly land" — not "match a Suppress spec." Stories also let us
cut: an action that doesn't serve any story doesn't ship in Stage 2.

## Re-imagining license

**No Stage 1 behavior is load-bearing.** `InfantryCombatantBehavior`, the
Stage 1 `EngagePosture` / `ApproachPosture` / `RegroupPosture` triad, the
inline "fire then sidestep then chase target" loop — all of it is up for
replacement. The Stage 1 docs are sealed in `complete/` so we have the
history, but Stage 2 actions are free to ignore that shape entirely.

### Specific Stage 1 failures we're fixing

These are the visible misbehaviors playtest surfaced. Stories address
each; calling them out here so they don't get re-invented by accident.

1. **Tunnel-vision pursuit.** A marine locked on an enemy will follow
   that enemy into a formation of other enemies — chasing one fleer
   straight into their squad's firing line. Fixed by Story I
   ("Engagement discipline") — target selection considers enemy density
   around the candidate target, not just LOS to it; pursuit re-evaluates
   on threat-density change.
2. **Statue-mode firefights.** Members fire from spawn cells with no
   micro-positioning between bursts. Fixed by Story G
   ("Cover-aware reposition") — short-range moves between shots.
3. **All-or-nothing posture.** Squad either pushes or holds, with no
   "wait for the right moment" pose. Fixed by Story A
   ("Garrison ambush") — overwatch posture distinct from engaged posture.
4. **No retreat ever.** Half-strength squads die in place. Fixed by
   Story B ("Pinned and broken") — `SurviveContact` goal.

## Spatial structure: zones, portals, POIs

The map exposes a hierarchical navigation layer the Stage 1 planner
ignored entirely:

- **`PointOfInterest`** — tagged building footprint
  (`LABORATORY`/`COMMS`/`DEPOT`/`RESIDENTIAL`) with an exterior anchor
  *and* a walkable interior anchor inside the structure.
- **`NavigationZone`** — a connected region of walkable cells (one zone
  per room indoors, one per open area outdoors). Knows its cell membership
  and the portals that border it.
- **`Portal`** — a 1-cell doorway linking two zones. AI can ask
  `zone.getPortalIds()` in O(1) for "what doorways does this room have."
- **`ZoneGraph`** — orchestrator. `zoneAt(x,y)`, `adjacentZones(zoneId)`,
  `areConnected(a,b)`.

Stage 1 actions operate on cells. Several Stage 2 stories *want* to
operate on rooms and doorways — "clear this room," "post a member at
each doorway," "fall back through the doorway behind me into a defended
room." Promoting zones/portals to first-class planner objects opens
those stories without forcing the planner to re-derive structure from
the cell grid every tick.

The integration with the existing **tactical-node** layer is layered,
not duplicated:
- Tactical nodes = curated, sparse, *meaningful* points (garrison spawn,
  charge site, fallback target). Hand-placed at map gen.
- Zones / portals = automatically detected from the grid. Every room
  exists in this layer whether or not it has a tactical node.
- A charge site (tactical node + POI interior anchor) lives *inside* a
  zone. The zone's portals are the doorways the planter's escort posts at.

## Reading order

1. Skim the stories. Mark the ones that excite you / that you'd want to
   see first in a Conquest run.
2. Look at the **Primitives map** at the bottom to see which actions /
   predicates / cost components each story demands.
3. Decide which 2–3 stories drive the first Stage 2 slice. The rest
   queue behind.

The point is to pick a small chord of stories that share primitives so
the first slice produces visible behavior on the playtest instead of
half-built infrastructure.

---

## Stories

### A. Garrison ambush ✅ SHIPPED (2026-05-18)

> A defender garrison holds position in a building, sees a marine fireteam
> push down a corridor toward them, and *waits* — holds fire until LOS
> stabilizes — then opens up with concentrated fire. Marines break LOS
> and arc around the building rather than running straight back into the
> kill zone.

**Setting:** Conquest. Defender squad GARRISON'd at a tactical node inside
a building. Marine fireteam approaching from a shuttle drop.

**Beats:**
1. Defenders see marines through a doorway, alert level → ENGAGED, but
   *don't* fire yet — distance too long, accuracy not worth the position
   reveal.
2. Marines cross a threshold (range, or LOS-time, or doorway entry).
   Defenders open fire with the whole squad on the same target.
3. First marine drops or takes meaningful damage. Surviving marines break
   LOS — duck behind the building corner — replan.
4. Marines pick a flanking approach (windows, second door, blow a wall in
   the long run). Defenders, seeing LOS lost, hold posture but reposition
   to cover the secondary angle.

**Observable signals:**
- Defender squad has a "holding" pose distinct from "engaged firing" pose
  (different debug-panel posture name)
- Visible burst of concentrated fire when the trigger lands, not a slow
  trickle of one-shot-per-tick
- Marines re-route via a different cell-path than the one they came from

**Primitives required:**
- `OverwatchPosture` (or "HoldFireUntilTrigger") — defenders sit at LOS
  but suppress their own fire until a trigger predicate
- A trigger predicate — `ENEMY_IN_KILL_ZONE` (range bucket + LOS-time)
- `BreakLOS` action for the marines — predicate `UNDER_FIRE_AT_LOS`
- Cover-quality scoring for the defender reposition (Story G primitive)

---

### B. Pinned and broken ✅ SHIPPED (2026-05-18)

> A marine fireteam pushes into a defender position, takes two casualties
> in quick succession, drops below half strength, and *breaks contact*
> — falls back to the previous covered position instead of dying in
> place. The surviving members re-form there, then the player can
> reinforce or re-attempt.

**Implementation note.** The original "hp/half-strength" trigger turned
into a one-way door — alive count never recovers without respawns, so
the goal would stick forever. Replaced with a recoverable **squad
morale** float: drains on hits and deaths, recovers when out of contact,
capped by alive/original ratio so heavy casualties cap the recovery
ceiling. Hysteresis (broken < 0.3, clears > 0.5) prevents flickering.
Story-bank primitives map updated below to reflect the
`MORALE_BROKEN` predicate that replaced `SQUAD_BELOW_HALF_STRENGTH`.
The legacy per-unit `rollFallbackOnHit` is now gated to skip GOAP-driven
infantry — `SurviveContact` + `BreakContact` is the sole infantry
retreat path. Mechs and civilians still roll the legacy timer.

**Setting:** Any. Triggered by casualty rate, not by mission.

**Beats:**
1. Squad of 4 takes fire, fires back, normal exchange.
2. Casualty 1 (member dies). Squad replans, keeps engaging.
3. Casualty 2 — squad now ≤50% strength. Replan picks a `SurviveContact`
   goal over `EliminateEnemies`.
4. Survivors path to the last known "safe" cell — the cell they last held
   when squad was full strength, or the nearest cover that's *not* in the
   defender's LOS.
5. At the fallback, squad re-forms (centroid converges), alert drops to
   SUSPICIOUS after `ENGAGED_DECAY_SECONDS`, posture becomes "regrouping"
   rather than "fleeing."

**Observable signals:**
- Survivors move *away* from the threat, not toward it
- They don't keep firing while moving (so they're visibly retreating, not
  bounding)
- They stop and re-form rather than running off the map
- Debug panel: goal flips from `EliminateEnemies` → `SurviveContact`

**Primitives required:**
- `SurviveContact` goal with HP-threshold relevance
  (`SQUAD_BELOW_HALF_STRENGTH` predicate)
- `BreakContact` action — move toward a covered cell that's *out of* the
  current threat's LOS
- A "last known safe cell" or "nearest cover-out-of-LOS" primitive in
  `TacticalScoring`
- Goal-priority logic — `SurviveContact` outweighs `EliminateEnemies` when
  both are relevant

---

### C. Bounding overwatch

> A marine fireteam advances on an objective in two halves. Half the
> squad fires from a covered position; the other half moves forward to
> the next cover. Once the movers reach cover, *they* start firing and
> the original firers move up past them. The squad leapfrogs forward.

**Setting:** Any open ground with intermittent cover. Conquest interiors
have hallway / room / hallway rhythm that fits.

**Beats:**
1. Squad in cover, contact ahead, can't all advance simultaneously
   without crossing fire lines.
2. Plan assigns 2 members `SuppressFromCover`, 2 members `BoundForward`.
3. Bounders reach next cover cell. SuppressFromCover team's effect
   timer expires.
4. Replan flips roles — old bounders take over `SuppressFromCover`,
   old suppressors become bounders.
5. Repeat until target cover is at firing range of the objective.

**Observable signals:**
- Two distinct postures visible simultaneously in one squad (so
  per-member action assignment is now mandatory — Stage 1's "every
  member gets every action" doesn't fit)
- One team is firing, the other team is moving and *not* firing
  (movement and shooting are exclusive for the bounder)
- Roles swap visibly between bounds

**Primitives required:**
- **Per-member action assignment in `SquadPlan.Step`** (Stage 1's TODO)
- `SuppressFromCover` action — fire-without-aim-to-kill at last known
  enemy cell, generates `EnemySuppressed` effect
- `BoundForward` action — move-only, target is a next-cover cell
  selected by `TacticalScoring`
- `RoleAssigner` actually used (Stage 1 built it, never wired) to split
  the squad into two role slots per plan

---

### D. Patrol intercept ✅ SHIPPED (2026-05-27)

> A patrol squad hears gunfire across the map, *doesn't* just keep
> patrolling, doesn't blindly sprint toward the noise — converges
> through cover, arrives at a flanking angle on the firefight, joins
> the engagement.

**Setting:** Conquest. Two defender squads — one GARRISON taking
fire from marines, one PATROL elsewhere on the map. Alert spreads via
the gunfire-alert system that already ships.

**Beats:**
1. Patrol squad is in PATROL routine, alert UNAWARE → SUSPICIOUS as
   gunfire triggers alert-spread.
2. Patrol squad replans. Goal flips from `Patrol` to `ReinforceContact`.
3. Plan picks a route through buildings / cover rather than the
   straight-line shortest path.
4. Patrol arrives at a position with LOS on the marines from a side
   angle (not the same angle the garrison is firing from).
5. Patrol opens fire — marines now under fire from two angles, replan
   on their end.

**Observable signals:**
- Patrol takes a longer-than-shortest path
- Final firing position is *not* colinear with the garrison's firing
  line (i.e., a flank angle, not behind the garrison)
- Player notices "oh shit, second squad inbound" before they're already
  in LOS — they get a debug-panel cue (goal name "ReinforceContact")

**Primitives required:**
- `ReinforceContact` goal — relevance scales with alert level and
  distance to known contact
- Pathfinding cost shaping — cover cells discounted, open-LOS cells
  penalized when an active contact is known
- A "flank angle preference" in firing-position scoring — penalize
  positions colinear with friendlies' fire line

---

### E. Mech-screened advance

> A marine squad accompanies a friendly mech down a corridor. The mech
> walks point. Marines pace behind it, using the mech's bulk as
> moving cover. When the mech engages an enemy, marines step out from
> behind it to add fire.

**Setting:** Any mission with mechs in the deployment. Marines + mech
spawned together or marines instructed to "follow that mech."

**Beats:**
1. Mech and marine squad both move toward an objective. Mech is ahead.
2. Marines' plan picks a cell *behind* the mech relative to threat
   direction (heuristic: marine's cell is on the friendly side of the
   mech-threat line).
3. Mech encounters contact, opens fire. Marines now have LOS past the
   mech.
4. Marines step out to either side, fire over/around the mech, stay
   close enough to keep the screen if mech moves.

**Observable signals:**
- Marines visibly stay *behind* the mech relative to threat — not
  parallel, not ahead
- When mech moves, marines move with it (not toward fixed terrain)
- When contact occurs, marines fan out *just enough* to fire — not so
  far that they lose the screen

**Primitives required:**
- `Escort` action variant where the anchor is a friendly unit, not a
  cell
- Threat-direction awareness — squad knows "where the enemy is" as a
  vector, can compute "behind the mech relative to it"
- Soft cover from non-static entities — mech body intercepts LOS the
  same way a doodad does

---

### F. Objective rush under fire

> A marine fireteam is mid-plant on a charge site, takes incoming fire,
> and *doesn't* break. The planter member keeps the plant timer
> running. Squadmates re-orient to suppress the threat so the plant
> finishes. This is *the* mission story — the planter doing its job
> under pressure.

**Setting:** Conquest, mid-charge-plant. Defender squad bears down on
the charge site while plant is in progress.

**Beats:**
1. Squad arrives at charge site, one member begins plant
   (mission-priority action).
2. Defender squad enters LOS, opens fire on the planter or the squad.
3. Non-planter members enter `EliminateEnemies` or `SuppressForObjective`
   posture — fire on the contact.
4. Planter member's plan is *not* interrupted. Planter does not move,
   does not return fire, stays on the action until the plant timer
   expires or the planter dies.
5. Plant completes → squad replans to `Defend` or `Withdraw`
   depending on follow-up orders.

**Observable signals:**
- One member visibly *not* shooting back, animation locked on
  plant interaction
- Other squad members reorient to face the threat *and* keep the
  planter behind them
- Plant timer visibly progresses through incoming fire

**Primitives required:**
- Mission-priority goal override — `CompleteObjective` outranks
  `SurviveContact` for the assigned member only (member-scope, not
  squad-scope)
- Per-member goal assignment — the squad has *two* concurrent goals
  (`CompleteObjective` for one member, `EliminateEnemies` for the
  rest). Stage 2 needs at least one squad / one member split.
- Existing `PlanterBehavior` retires; its work becomes a
  `PlantCharge` action under `CompleteObjective`

---

### G. Cover-aware reposition ✅ SHIPPED (2026-05-18)

> A squad in a firefight doesn't stand still and doesn't run randomly.
> Between bursts, individuals shift one or two cells — from "edge of
> doorway" to "corner of doorway," from "behind low cover" to "behind
> high cover" — to refresh cover or open a better firing angle.

**Setting:** Any sustained firefight. Foundational to the others.

**Beats:**
1. Squad engaged at range. All members in cover-of-some-kind.
2. After a burst of fire, member's `repositionCooldown` expires.
3. Member evaluates: is my *current* cover still the best nearby
   cover relative to the *current* threat? If a better cell exists in
   walking range, sidestep.
4. Repeat across the firefight — visible micro-movement, not
   statue-like exchange.

**Observable signals:**
- Marines aren't motionless between shots — they shift a cell or two
- Movement is short (1–3 cells) and toward cover, not toward the
  threat
- Different squad members shift at different times (decorrelated
  cooldowns)

**Primitives required:**
- Real cover model — doodads carry cover quality (per facing if
  affordable), not just "this cell is a wall"
- `RepositionToCover` action — short-range move that improves cover
  score against the current threat direction
- Per-member reposition cooldown (already exists via
  `REPOSITION_CHANCE` — re-frame as predicate `CAN_REPOSITION`)

---

### I. Engagement discipline

> A marine is firing on an enemy. The enemy takes hits, breaks LOS, and
> sprints away — toward its squadmates. The marine *does not follow*.
> Instead the squad re-evaluates: who's the highest-value target reachable
> *without* walking into a known threat cluster? If chasing the runner
> means closing on three more enemies, the runner stops being the target.

**Setting:** Any firefight where enemies aren't all in one spot — a
defender squad with one member forward as a spotter, a patrol overlapping
a garrison's fire arc, etc.

**Beats:**
1. Marine engages enemy at acceptable range with cover. Hits land.
2. Enemy ducks, breaks LOS, paths away from the marine — toward its
   squadmates.
3. Marine's target re-evaluation runs (cheap, every N ticks or on
   target-lost). It scores candidate targets by `threat = own_LOS_value
   - threat_density_at_target_cell`. Chasing the runner has high
   threat density (their squadmates).
4. Marine drops the runner, picks a new target — either another visible
   enemy or no target (hold cover, wait for next contact).
5. If *no* targets pass the threat-density gate, squad holds position
   rather than pushing into the unknown — UNAWARE/SUSPICIOUS posture
   instead of ENGAGED-pursue.

**Observable signals:**
- Marine *stops* firing and *stops* moving when its target breaks LOS
  and runs into a formation
- Marine doesn't walk into a room full of enemies because one wounded
  guy fled into it
- Squad cohesion is visibly preserved — no one member strung out
  10 cells ahead of squadmates chasing a single target
- Debug panel: posture flips from `Engage` back to `Overwatch` or
  `Approach` when LOS is lost, *not* to "chase that one guy"

**Primitives required:**
- `THREAT_DENSITY_AT_TARGET` predicate — bucketed count of enemies
  within radius of the candidate target's cell
- Target-scoring cost penalty: enemies surrounded by other enemies cost
  more to engage than isolated ones
- **Squad cohesion as a hard constraint on individual pursuit** — a
  marine cannot path more than N cells from the squad centroid while in
  ENGAGED posture without an explicit `Bound` or `Flank` action
  assignment
- Pursuit gating — when LOS is lost, default action is "wait at last
  good firing position," not "move to last known enemy cell"

---

### H. Last-stand objective camper

> A defender squad parked on an objective marine wants to capture has
> only one member left. That member doesn't run. Doesn't flee. Stays
> on the objective and fires until killed. Mission-priority overrides
> survival.

**Setting:** Conquest, late game, defender side of an objective. Or
flipped — marine squad guarding a captured charge site against a
defender counterattack.

**Beats:**
1. Defender squad reduced to one member. Normal squad logic would
   `SurviveContact` (Story B).
2. But the squad's `assignedNode` is a `MUST_HOLD` priority node (new
   role / flag on `TacticalNode`).
3. Goal-priority logic: `HoldPosition(node)` outranks `SurviveContact`
   when the node is `MUST_HOLD`.
4. Last member stays on the node, fires until dies.

**Observable signals:**
- Single remaining defender doesn't retreat
- Posture visibly "holding" rather than "withdrawing"
- Debug panel: goal stays `HoldPosition` even at 1/4 strength

**Primitives required:**
- `MUST_HOLD` flag on `TacticalNode`
- `HoldPosition` goal with relevance gated on the node flag
- Goal-priority logic — mission-tagged goals outrank survival goals

---

### J. Sabotage cordon

> A marine squad enters a `LABORATORY` POI to plant a charge. They don't
> all crowd around the planter. The room has three doorways; the
> squad clears the room, then one member begins the plant while the
> other three post one per doorway, facing outward, fire-ready. Anyone
> who comes through a doorway eats concentrated fire from the member
> covering it. Plant completes, squad falls back as a unit through the
> primary doorway.

**Setting:** Sabotage mission inside an indoor zone with multiple
portals. Conquest charge sites that land inside buildings already fit
this pattern.

**Beats:**
1. Squad arrives at the POI interior anchor. Path was secure (Story K
   for room-by-room clear) or the room was empty on arrival.
2. Planner reads the current zone via `ZoneGraph.zoneAt(anchor)`. Pulls
   `zone.getPortalIds()` — say three doorways.
3. `RoleAssigner` slots the squad: 1 × `Planter` on the charge cell,
   3 × `HoldPortal(portalId)` with each member assigned a distinct
   doorway.
4. Plant timer runs. If contact arrives at portal P, the member covering
   P opens fire; alert spreads via existing system; other portal-holders
   stay on their doorway (one squad doesn't peel all members to one
   threat — the other doorways still need cover).
5. Plant completes → goal flips. Squad falls back as a unit through the
   primary doorway (the one closest to the LZ / exfil point), members
   detaching from portals one at a time so the rear remains covered.

**Observable signals:**
- Squad members visibly *space out* to doorways rather than clustering
  on the planter
- Each member faces *their* doorway, not a common direction
- A defender approaching through portal P draws fire from one
  member, not all four (cordon discipline)
- Squad exits as a coordinated group through one doorway, not by
  members independently fleeing
- Debug panel: per-member action shows `HoldPortal[#]` /
  `Planter` / etc. (Stage 2 per-member assignment lets us actually
  display this)

**Primitives required:**
- `ZoneGraph.zoneAt(x,y)` lookup in the planner (cheap — O(1))
- `HoldPortal(portalId)` action — anchor at the cell facing the
  doorway, target = first enemy that crosses the portal cell
- `Planter` action (mission-priority; from Story F)
- Per-member action assignment (Story C)
- `RoleAssigner` slots: 1 planter + N portal-holders for an N-portal
  room
- Cordon discipline rule — a member assigned `HoldPortal(P)` doesn't
  re-target enemies coming through `HoldPortal(P')` unless their own
  portal is clear

---

### K. Room-clear sweep

> A marine squad pushes into a multi-room building. Doesn't just sprint
> to the objective room. *Clears each room as they go* — enter a zone,
> sweep for enemies, secure (suppress / kill / confirm empty), advance
> to the next zone through the adjacent portal.

**Setting:** Any indoor multi-zone push. Sabotage missions where the
target POI is several rooms deep.

**Beats:**
1. Squad at zone A. Plan target is a charge site in zone D, several
   portals away.
2. Planner queries `ZoneGraph.areConnected(zoneA, zoneD)` to confirm a
   route exists, then plans the zone-by-zone path: A → B → C → D.
3. Squad enters zone B through portal A-B. Pauses inside the portal
   threshold while members fan out. Checks for enemies in zone B.
4. Zone B clear → squad advances to portal B-C. Holds at the threshold.
5. Zone C contains enemies. Squad engages from inside the doorway frame
   (cover from the portal walls — Story G primitive). Doesn't push into
   the room until the room is suppressed.
6. Zone C cleared (kills + alert decay) → continue to zone D.

**Observable signals:**
- Squad doesn't run straight from spawn to the objective through three
  rooms full of enemies
- Each room transition has a visible *pause* — members fan to cover
  positions inside the new zone before continuing
- Engagement happens from doorways (covered) before entering rooms
- Debug panel: posture cycles `EnterZone[B]` → `ClearZone[B]` →
  `EnterZone[C]` → `ClearZone[C]` etc.

**Primitives required:**
- `EnterZone(targetZoneId)` action — move squad to inside the portal
  threshold of `targetZoneId`, fan to cover within that zone
- `ClearZone(targetZoneId)` action — engagement loop scoped to enemies
  *inside* `targetZoneId`. Won't pursue enemies across portals.
- Zone-path planning — instead of cell-path A* to objective, do a
  zone-graph BFS first to get the room sequence, then plan one
  `EnterZone` + `ClearZone` per room. Coarser planner search, much
  fewer expansions.
- `ZONE_CLEAR(zoneId)` predicate — bucketed: no live enemies in zone

---

### L. Choke-point ambush ✅ SHIPPED (2026-05-18)

> A defender squad knows marines are pushing toward them down a
> corridor — single approach, single doorway entry into the defender's
> room. The squad doesn't spread out, doesn't go forward. *All four
> members aim at the same portal cell.* First marine through the
> doorway eats a wall of concentrated fire.

**Setting:** Defender garrison in a room with one ingress portal,
contact pushing through that portal.

**Beats:**
1. Defender squad GARRISON'd in zone D. Adjacent zone C is the route
   the marines must take.
2. Alert level → SUSPICIOUS or ENGAGED as marines enter C / gunfire
   triggers spread. Defender squad replans.
3. Plan picks `ChokePointHold(portalC-D)`. All members assigned to it.
4. Each member moves to a cell with LOS on the portal cell — different
   cells, same firing target. Cover-quality scoring (Story G) picks
   the best LOS-to-portal positions per member.
5. Marine crosses the portal threshold. Predicate
   `ENEMY_IN_PORTAL_CELL(portalC-D)` flips true. Every member fires
   that tick — concentrated burst, not a trickle.
6. Marine drops or breaks back through. Defenders hold position
   (the next marine through gets the same treatment).

**Observable signals:**
- Defenders don't push into the corridor — they stay in their room
- All defender members are visibly oriented toward the same doorway
- First contact through the doorway draws full-squad fire, not staggered
- Defenders *don't* break formation to chase, even after the first
  marine drops (Story I: engagement discipline reinforces this)

**Primitives required:**
- `ChokePointHold(portalId)` action — squad-level, slots = each member
  takes a different LOS-to-portal cell
- `ENEMY_IN_PORTAL_CELL(portalId)` predicate (bucketed: any vs none)
- Concentrated-fire trigger — when the predicate flips true, *all*
  members in the action shoot the same tick (not "any one of them
  rolls a fire chance")
- Single-portal vs. multi-portal awareness — if zone has multiple
  ingress portals, fall back to Story J (cordon) instead

---

## Anti-stories — explicitly NOT shipping in Stage 2

Things we're choosing *not* to do, with rationale, so we don't drift into
them on accident.

- **Squad-vs-squad coordination across squads.** Two defender squads
  *coordinating* a pincer is out of scope. The alert system spreads
  awareness; planning stays per-squad. Cross-squad coordination is
  emergent-only in Stage 2 (Story D's flank angle is one squad
  reading the other squad's fire line, not negotiating with it).
- **Verbal callouts / chatter.** Tempting. Skipped — audio bandwidth
  and string content. Stage 2 ships text-only via debug panel.
- **Morale per-unit / panic.** Squad-level morale (Story B) covers the
  retreat-and-reconstitute loop with a single `Squad.morale` float —
  drain on hits/deaths, recover when out of contact, cap by
  alive/original ratio. Per-unit panic / friendly-flee chains add
  complexity without serving a story.
- **Heroic charges / overrides** beyond Stories F + H. The "one member
  ignores survival" pattern serves the mission. The "whole squad
  ignores survival" pattern is anti-fun in a roguelike-ish loop.

---

## Primitives map

What each story needs, so we can spot the shared dependencies.

| Story | New predicates | New actions | New goals | Infra |
| --- | --- | --- | --- | --- |
| A. Garrison ambush ✅ | `ENEMY_IN_KILL_ZONE`, `UNDER_FIRE_AT_LOS` | `OverwatchPosture`, `BreakLOS` | `GarrisonAmbush`, `RecoverFromAmbush` | Cover-quality scoring (also G); kill-zone gate via `Squad.holdsFireUntilKillZone` + LOS-stability ticks |
| B. Pinned and broken ✅ | `MORALE_BROKEN` | `BreakContact` | `SurviveContact` | Squad morale state (drain/recover/cap/hysteresis); cover-out-of-LOS reuses `TacticalScoring.findFallbackPosition` |
| C. Bounding overwatch | `ENEMY_SUPPRESSED` | `SuppressFromCover`, `BoundForward` | — | **Per-member action assignment**; `RoleAssigner` actually used |
| D. Patrol intercept ✅ | (uses alert spread) | `FlankApproach` | `ReinforceContact` | Flanking waypoint algorithm (90° off garrison axis); RoutinePatrol SUSPICIOUS yield |
| E. Mech-screened advance | `BEHIND_FRIENDLY_RELATIVE_TO_THREAT` | `EscortFollow` (anchor=Unit variant) | — | Soft cover from non-static entities; threat-direction vector |
| F. Objective rush under fire | (mission predicates) | `PlantCharge` | `CompleteObjective` | **Per-member goal override**; retires `PlanterBehavior` |
| G. Cover-aware reposition ✅ | `CAN_REPOSITION` | `RepositionToCover` | — | Per-facing cover (4-way N/E/S/W) on doodads; per-unit `repositionCooldown` (1.5s) |
| I. Engagement discipline | `THREAT_DENSITY_AT_TARGET` | (target picker rewrite, no new action) | — | **Threat-density-aware target scoring**; pursuit gating; squad cohesion as hard constraint |
| H. Last-stand camper | `NODE_IS_MUST_HOLD` | — | `HoldPosition` | `MUST_HOLD` flag on `TacticalNode`; goal priority |
| J. Sabotage cordon ✅ | (mission predicates) | `HoldPortalCordon` (planter + portal slots) | `CordonForPlant` | `ZoneGraph` queries; per-member assignment; cordon discipline via positioning |
| K. Room-clear sweep ✅ | (custom-plan) | `EnterZone`, `ClearZone` | `SecureObjectiveZone` | Zone-path planning (BFS at room level, A* at cell level inside) |
| L. Choke-point ambush ✅ | `ENEMY_IN_PORTAL_CELL` | `ChokePointHold`, `GarrisonCordon` (multi-portal degenerate) | (folded into `GarrisonAmbush`) | Concentrated-fire trigger; portal-LOS scoring |
| M. Room breach ✅ | — | `BreachAndAdvance` | `BreachToEngage` | Two-phase stack-up+push; `Squad.breachStackupTimer`; soft zone-mismatch target bias |

## Cornerstones — likely the first Stage 2 slice

Looking at the dependency graph, four pieces unlock the most stories:

1. **Engagement discipline (Story I).** Fixes the most-visible Stage 1
   misbehavior (tunnel-vision pursuit). Cheapest of the four —
   target-scoring rework + a cohesion clamp on movement. **Pure win on
   day 1 of playtest.** Done in isolation, the rest of Stage 1 still
   looks like Stage 1, but the bad behavior goes away.
2. **Cover model on doodads.** Direct dependency of A, B, C, G — half
   the stories. Without it, cover-quality scoring is the cell-grid
   approximation we have today, and every "move to cover" story
   degenerates to "move to a wall."
3. **Per-member action assignment.** Direct dependency of C, F, J, L —
   the stories that are *most visibly* different from Stage 1. Without
   it, Stage 2 looks like "Stage 1 with more action names."
4. **Zone/portal-aware planner queries.** Direct dependency of J, K, L
   and sharpens A, B, D, F. The data already exists (`ZoneGraph`,
   `PointOfInterest`); the lift is teaching `WorldStateBuilder` and the
   action library to read it. Cheap to land, large story payoff —
   sabotage missions get their signature behavior, room-clear becomes
   possible.

How existing stories sharpen with cornerstone 4:
- **A. Garrison ambush** — "the kill zone" becomes a specific portal cell,
  not a hand-tuned radius.
- **B. Pinned and broken** — "fall back to a safe cell" becomes "fall
  back through the doorway behind us into a defended zone."
- **D. Patrol intercept** — the flank angle becomes "approach via a zone
  the contact's zone is adjacent to but not visible from."
- **F. Objective rush under fire** — overlaps with Story J; J is the
  zone-aware version of F.

Recommended slicing:
- **Slice 1 ✅ (immediate playtest win):** Story I — engagement
  discipline. Target picker + cohesion clamp. Shipped.
- **Slice 2 ✅ (sabotage signature):** Stories J + K — zone/portal-aware
  cordon + room sweep. Shipped 2026-05-18 along with the FireStance
  accuracy modifier and the retirement of `PlanterBehavior` into a
  GOAP role slot.
- **Slice 2.5 ✅ (Story B, bumped up):** The planter-vs-fall-back
  conflict made B's `SurviveContact` more urgent than Slice 5's original
  ordering — implemented with squad morale (drain/recover/cap/
  hysteresis), then gated `rollFallbackOnHit` to skip GOAP-driven
  infantry. Shipped 2026-05-18.
- **Slice 3 ✅ (visible cover combat):** Stories G + A + L. Cover model
  on doodads + reposition + overwatch + choke-point hold. Shipped
  2026-05-18 (session 2). Per-facing cover (4-way N/E/S/W) on doodads,
  cooldown-gated `RepositionToCover`, `OverwatchPosture` with a
  kill-zone gate, `ChokePointHold` for single-portal rooms +
  `GarrisonCordon` for multi-portal degenerate, `RecoverFromAmbush`
  goal wiring `BreakLOS` into goal selection.
- **Slice 3.5 ✅ (room breach insert):** Story M — room breach. Inserted
  from playtest. `BreachAndAdvance` two-phase stack-up-then-push action
  + `BreachToEngage` goal + soft zone-mismatch target bias. Shipped
  2026-05-18 (session 2).
- **Slice 4 (squad coordination):** Stories C + F. Per-member assignment
  + bounding overwatch + objective-rush-under-fire. F may collapse
  into J by this point (J already covers planter-under-fire via the
  cordon goal hierarchy).
- **Slice 5 (remaining mission goal priority):** Story H — last-stand
  `HoldPosition` on `MUST_HOLD` tactical nodes. Story B already shipped
  in Slice 2.5.
- **Slice 6 ✅ partial (cross-squad emergence):** Story D shipped
  2026-05-27 — `ReinforceContact` goal + `FlankApproach` action with
  flanking waypoint algorithm (90° off garrison axis). Story E (mech
  screening) remains; depends on the parked mech GOAP work
  ([13-mech-goap.md](13-mech-goap.md)).

## What this doc is for

When we start a Stage 2 task doc — `11-cover-model.md`, `12-per-member-assignment.md`,
etc. — it cites the stories it serves. "Done" for that task is "Story C
visibly plays in a Conquest run." Not "the action implements the spec."

## What this doc is NOT for

- **Implementation specs.** The numbered task docs (11+) own those.
- **Stage 3 (mission goals).** Story F gestures at it; the full mission
  goal library waits until Stage 2 lands.
- **Priorities outside the cornerstones list.** Reorder when the first
  slice playtests.
