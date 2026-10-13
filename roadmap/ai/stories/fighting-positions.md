# Fighting positions

Status: IN PROGRESS — slices 1 (substrate) and 2 (digging in) built; slice 3 (defend-area fill) next

Written: 2026-10-13

Updated: 2026-10-13 — substrate and dig-in built (`FightingPositionIndex`, `DigInSystem`, per-marine morale drain halved, `battle.infantry.fightingPositions`); fort sandbags and trenches are not yet destructible, so only plain sandbag runs dissolve under fire.

## Intent

Trenches and sandbag lines are already on the map as walkable heavy cover, and a
marine standing in one is already harder to hit. What they are not is a *place a
squad can occupy*: nothing names them, nothing sends a squad into them, and
nothing about standing in one is different from standing behind any other prop
of the same cover tier.

A **fighting position** is a run of prepared ground — trench floor, sandbag
parapet, and the standable ground immediately either side of a parapet — large
enough for a full squad to occupy. A marine who settles onto one of its cells
**digs in** after a short delay and, while dug in, receives a passive benefit on
top of the cover the cell already gives. A squad ordered onto a position fills
its cells; whoever does not get a cell uses the ordinary cover search nearby.

The player-facing word was *garrisonable*. The noun here is *fighting position*
because "garrison" already names something specific in this codebase — the
defender force holding a node, `CompoundGarrisonSystem`, standing to — and a
second meaning would make every one of those sentences ambiguous.

## Laws

- **The benefit is per marine, not per squad.** A marine is dug in or not on
  their own cell and their own clock. A four-person squad in a twelve-cell
  position gets four dug-in marines; a twelve-person squad in a ten-cell run gets
  ten, and two marines without the bonus. Understrength squads need no special
  case, and neither does overflow.
- **Cells are claimed by marines, not positions by squads.** A long belt trench
  holds as many squads as it has cells. A squad sent onto a partly occupied run
  takes the free cells nearest its order, as a contiguous block where it can so
  fire teams stay together. Nobody is evicted.
- **The twelve-cell floor is a generation rule, not an occupancy rule.** A run
  shorter than a full squad is not a fighting position at all; it is still cover.
  Once a run qualifies, any number of marines may use it.
- **Positions have no stored front.** Trench cover is isotropic on its own cell,
  and a parapet already publishes facing cover onto standable ground on both
  sides of it (`DoodadService`'s neighbour bleed). A sandbag line can therefore
  be manned from either side; changing sides means crossing it, which is the
  honest cost. The threat bearing chooses which side, not the position.
- **Derived from live map state, not authored separately.** A position is read
  from the prepared-ground props actually standing, the way frontage is read
  from apertures actually open. Every stamper that lays trench or sandbag —
  forts, Thunder Run's aprons, gun bays — produces positions without being
  touched, and a destroyed parapet shrinks or dissolves the run it belonged to.
  A run that falls below twelve cells stops being a position; its marines lose
  dug-in status and keep whatever cover remains.
- **The bonus does not repeat cover.** Cover already reduces damage
  (`DamageResolver`) and stops rounds (`BallisticResolver`). Dug-in pays out in
  something cover does not: composure under fire.
- **Infantry only.** Mechs, vehicles, drones and emplacements are out of scope
  until a special emplacement is designed for them.
- **Either side.** A position is neutral ground. Defenders standing in their own
  belt dig in exactly as marines do, and an attacker who takes a trench takes its
  benefit.

## Slices

### 1. Substrate — what is a fighting position

- A tileset doodad declares itself prepared ground: a `fightingPosition`
  property on the doodad entry (`trench` or `parapet`) carried on `DoodadDef`.
  Set it on the `fort-earthworks` trench and sandbag pieces. Classify by a
  declared property, never by id prefix.
- A per-battle derived index answers `isFightingPositionCell(x, y)` and
  `runIdAt(x, y)`: trench cells, parapet cells, and walkable cardinal neighbours
  of a parapet; grouped into 4-connected runs; runs under twelve cells dropped.
- Rebuilt when a prepared-ground prop is destroyed (the existing cover-prop
  destruction path), not per tick.
- Unit tests on small hand-built grids: a trench run, a parapet manned both
  sides, a run of eleven dropped, a destroyed parapet splitting a run.

### 2. Digging in and the benefit

- Per infantry marine: settled on a fighting-position cell for a dig-in delay
  (start at 2 simulated seconds) makes the marine dug in. Moving off the cell, or
  the cell leaving the index, ends it at once.
- Benefit: morale drain charged to the fire team for a hit or near-miss on a
  dug-in marine is scaled down (start at half). Recovery is unchanged.
- Behind `battle.infantry.fightingPositions` (default on once evidence agrees)
  so a scene's control loop can switch it off for itself.
- Considered and deferred: a braced `FireStance` above `STANCED`. It overlaps
  the "Sandbagged rest" integral system in `integral-system-slate.md`; decide
  there before adding it here.

### 3. Getting a squad into one

- The existing player **defend area** order prefers fighting-position cells in
  its prepared-position search, with free cells nearest the order first and
  facing chosen by the squad's believed threat bearing. Members who cannot claim
  a cell fall through to the existing search unchanged.
- Cell claims are exclusive between marines across squads.
- Reflexes that move a settled marine for cover (`RepositionToCover`,
  `LastStandHold`) leave a dug-in marine where they are; grenade escape still
  applies. Plant sites keep consulting `LaneSidestep.isStepping`.
- A dedicated "occupy position" click and commander use of positions are
  follow-ups, not this story.

### 4. Evidence and presentation

- A `BehaviorScene`, `fighting-position`: a squad in a belt trench under rifle
  fire, played with the toggle on and again off (the control). Verdicts: marines
  dug in, fire-team broken ticks, and that the control's cover readings match the
  subject's — so any difference is the bonus and not the ground.
- An overflow loop: a twelve-person squad ordered onto a ten-cell-free run, with
  the two overflow marines in ordinary cover.
- A battle overlay marking fighting-position cells, claimed cells and dug-in
  marines.

## Acceptance

- Every fort belt and sandbag apron on a generated map produces positions with no
  change to its stamper, and a run under twelve cells produces none.
- A marine settled on a position cell digs in after the delay; one who leaves is
  no longer dug in on that tick.
- Under identical fire, the dug-in fire team breaks later than the control, while
  damage taken is unchanged within run-to-run spread.
- Two squads ordered onto one long trench occupy disjoint cells; overflow marines
  take ordinary cover and are not dug in.
- `ai-nouns.md` (behaviour) and `mapgen-nouns.md` (the substrate) absorb the
  standing model when this ships.
