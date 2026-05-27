# 11 — Stage 2 Foundation

**Stage 2 prep.** Bookkeeping doc for the work that unlocks parallel
subagent fanout on the [Stage 2 story bank](../stories/10-tactical-stories.md).

Tier 0 is the bottleneck — shared types every story touches. Done
in-context (not subagent), three small commits. Tier 1 fans out to ~4
subagents once Tier 0 lands.

## Tier 0 — shared types (in-context, sequential)

### 0a. Predicate surface for Stage 2

Pre-declare every predicate the story bank cites in one commit, with
**stub evaluators returning false** in `WorldStateBuilder`. Subagents
implementing a story can then add the real evaluator next to the action
without touching shared enum values, avoiding merge collisions.

New predicates (each tied back to its story):

- `SQUAD_BELOW_HALF_STRENGTH` — Story B
- `ENEMY_IN_KILL_ZONE` — Story A
- `UNDER_FIRE_AT_LOS` — Story A
- `ENEMY_SUPPRESSED` — Story C
- `BEHIND_FRIENDLY_RELATIVE_TO_THREAT` — Story E
- `CAN_REPOSITION` — Story G
- `ZONE_CLEAR` — Story K
- `ENEMY_IN_PORTAL_CELL` — Story L
- `NODE_IS_MUST_HOLD` — Story H
- `THREAT_DENSITY_HIGH_AT_TARGET` — Story I

**Role-parameterized predicate convention.** Predicates that *seem* to
need parameters (which portal? which zone?) are implicitly scoped to the
squad's current target/objective. `ENEMY_IN_PORTAL_CELL` means "the
portal the squad's `ChokePointHold` action is watching." `ZONE_CLEAR`
means "the zone the squad's `ClearZone` action targets." The evaluator
reads the squad's currently-assigned target portal/zone from squad
fields the action set up. Keeps predicates as flat bits while letting
actions parameterize.

### 0b. SquadPlan.Step → per-member role slots

Stage 1's `Step` is `(Action, List<Unit> assignedMembers)` and every
alive member is bound to every step. Stories C / F / J / L need
*different members doing different things in parallel within a single
plan step*.

Reshape to:

```java
public static final class Step {
    public final Action action;
    /** Slot name → assigned members. Filled by RoleAssigner. */
    public final Map<String, List<Unit>> assignments = new LinkedHashMap<>();
}
```

Add to `Action`:

```java
default List<RoleAssigner.Slot<Unit>> roles(Squad squad, BattleSimulation sim) {
    // Stage 1 compat: one slot, all members, no preference.
    return List.of(new RoleAssigner.Slot<>(
        "any", squad.aliveMembers, Scorers.constant(0f)));
}
```

`GoapInfantryBehavior.update` looks up the member's slot via
`Step.slotOf(unit)`. If null → skip this tick. Otherwise execute the
action (the action may branch on slot name for stories like J's cordon
where `"planter"` and `"portal:42"` do different things).

### 0c. Wire RoleAssigner into replanIfNeeded

`RoleAssigner` exists from Stage 1 (slot scoring, greedy + swap
improvement). Currently called nowhere. After `Planner.plan` returns,
for each step:

```java
List<Slot<Unit>> slots = step.action.roles(squad, sim);
Map<String, List<Unit>> assignment =
    RoleAssigner.assign(aliveMembers, slots);
step.assignments.putAll(assignment);
```

The Stage 1 actions' default `roles()` returns one "any" slot taking all
members — same behavior as today, no parity regression.

## Tier 1 — parallel subagent fanout (after Tier 0)

Four independent subagent tasks. Each is a small file footprint with no
cross-dependencies once Tier 0 has landed.

### Subagent A — Cover model on doodads

**Scope.** Extend `Doodad` with cover-quality data (per facing if cheap;
omnidirectional if not). Add `TacticalScoring.bestCoverCell(threatDir,
nearCell, radius)` returning the highest-cover walkable cell within
range. Author cover values for existing doodad kinds.

**Files.**
- `Doodad.java` — add cover field(s)
- `TacticalScoring.java` — `bestCoverCell` + a cover-aware variant of
  `firingPositionFor`
- Wherever doodads are authored (`DistrictTheme` / map gen) — fill in
  cover values

**Done when.** A scorer can rank cells by "cover quality relative to a
known threat direction" and pick a discriminative best one. Stories
A, B, C, G are unblocked.

### Subagent B — Zone-aware planner helpers

**Scope.** Build the convenience layer between `BattleSimulation.getZoneGraph()`
and the action library. Add to a new `world/ZoneQueries.java`:

- `int squadCurrentZone(Squad)` — zone containing the squad centroid
- `int objectiveZone(Squad)` — zone the assigned objective lives in
- `List<Integer> portalsOf(int zoneId)` — already on `NavigationZone`,
  but wrap for symmetry
- `boolean zoneClear(int zoneId, Faction enemyFaction, sim)` — no live
  enemy combatants of the named faction in that zone
- `List<Integer> zonePathBfs(int from, int to)` — sequence of zone ids
  for Story K's room-by-room planner

**Files.**
- New `battle/ai/goap/world/ZoneQueries.java`
- `WorldStateBuilder.java` — optional thin helpers

**Done when.** Stories J / K / L can ask "what zone am I in," "what
portals does it have," "which adjacent zone is the next room toward the
objective" without re-deriving from the cell grid.

### Subagent C — Goal-priority bucket system

**Scope.** Today `Goal.pickMostRelevant` returns highest `relevance()`.
Stories B / F / H need *priority buckets* — mission goals override
survival goals override engagement goals, regardless of raw relevance.

Add `Goal.priority()` returning enum `{ MISSION, SURVIVAL, ENGAGEMENT,
IDLE }`. Rewrite `pickMostRelevant` to "highest priority bucket with
relevance > 0; ties broken by relevance." Default for Stage 1's
`EliminateEnemiesGoal` is `ENGAGEMENT`.

**Files.**
- `Goal.java`
- `goals/EliminateEnemiesGoal.java`

**Done when.** A new goal can declare itself MISSION-tier and outrank
ENGAGEMENT regardless of relevance score, without `pickMostRelevant`
caring about specific goal classes.

### Subagent D — Threat-density target picker (Story I prep)

**Scope.** Rewrite `TacticalScoring.pickTarget` (or wrap it) so the
score per candidate enemy includes a *penalty* proportional to the
count of other enemies within a radius of the candidate's cell.
Pursuit gate: when a previously-engaged target leaves LOS *and* moves
toward a high-density cell, the picker drops them rather than
re-pursuing.

**Files.**
- `TacticalScoring.java` (collision risk with Subagent A's edits —
  sequence A → D, or split methods between them)

**Done when.** A marine no longer locks onto and chases a wounded
fleer into the fleer's squad. Validated by a unit test feeding a
canned squad + lone wounded fleer + cluster of friends, asserting the
picker selects the cluster's outer members or no-target rather than
the fleer.

## Tier 2 — story fanout (after Tier 1)

Each story becomes a self-contained subagent task using only the
foundations above. Slice 1 of the story bank (Story I — engagement
discipline) is unblocked the moment Subagent D ships. Slice 2 (J + K)
needs Subagent B's zone queries. Slice 3 (G + A + L) needs Subagent A's
cover model.

The story doc ([10-tactical-stories.md](../stories/10-tactical-stories.md)) is
the per-story spec; this doc is just the foundation. Don't copy story
details here — keeps the story bank single-source.

## What this doc is NOT for

- **Implementation specs for the foundations.** Tier 0 lives in the
  source; tier 1 subagents get the section above as a prompt and own
  their implementation choices.
- **Story specs.** Story bank (10) owns those.
- **Sequencing of Tier 1 subagents.** They can all run in parallel
  except A and D which both touch `TacticalScoring` — sequence those
  two or pre-split the file.
