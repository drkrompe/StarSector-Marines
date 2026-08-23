# S2B — Smoke grenades

> Change the line of sight instead of dealing damage.

Status: PLANNED — depends on the shared special-equipment identity established
by `s2a-anti-materiel-rifle.md`; it is not a `WeaponDef` migration.

Written: 2026-08-23

Read `progression-nouns.md`, `company-view-nouns.md`, and
`fog-of-war-nouns.md` before implementing this story.

## Tactical identity

A **smoke grenade** is a short arcing throw that creates a temporary,
simulation-owned line-of-sight blocker. It lets a fire team cross an exposed
lane, sever contact during a withdrawal, or approach a defended portal without
causing damage.

Smoke is neutral terrain for its lifetime. It blocks player and enemy direct
observation under the same rule, does not block movement, does not erase
contact memory, and grants no faction a one-way firing exception. Initial
balance should start with one or two grenades per carrier, a cloud several
cells across, and enough duration for one deliberate bound; radius and duration
remain playtest values.

## Simulation and visibility authority

- A thrown grenade follows the existing arcing projectile path, then creates a
  deterministic transient smoke field whose occupied cells and expiry live in
  battle simulation state.
- The shared tactical line-of-sight query consumes smoke opacity, so targeting,
  firing-position tests, cover/route evaluation, and AI observation agree.
- Fog of war remains presentation authority only. It consumes the changed
  observation footprint and invalidates stationary contributors when smoke
  appears, moves if drift is later added, or expires. Smoke does not live as a
  private exception inside `FogOfWarService`.
- Direct contact belief remains after the view is cut and decays under the
  shipped perception rules. Audio still crosses the cloud. Indirect fire may
  act on whatever belief its normal policy permits; direct fire may not trace
  through opaque smoke.
- The rendered cloud must match the simulation footprint closely enough that a
  player can read which firing lanes are blocked. Decorative wisps may extend
  beyond it but cannot imply a safe cell that remains visible.

## Gameplay-AI integration

Smoke is a planned utility action, not reactive opportunity fire.

### Offensive crossing

1. A squad assignment still requires progress, but every reasonable route
   crosses a sufficiently exposed lane covered by identified hostile direct
   fire.
2. `DeploySmoke` scores a landing footprint between that threat axis and the
   cells the moving fire team must traverse. A throw at the carrier's feet is
   a fallback for withdrawal, not the default assault placement.
3. The planner reserves the lane, assigns one equipped carrier, pauses that
   carrier for the throw, and begins the bound only after the cloud becomes
   opaque.
4. The route is accepted only when the team's estimated crossing time fits
   within the useful remaining smoke duration.

### Withdrawal

- A squad pursuing `SurviveContact` may deploy smoke when the cloud can sever
  an identified direct-fire axis and a reachable fallback path actually uses
  that concealment.
- Smoke supplements `BreakContact`; it does not override morale, teleport a
  squad to safety, or make survivors stand still to throw when immediate
  movement is the safer answer.

### Coordination and response

- One active reservation exists per relevant lane/plan. A second carrier does
  not throw into a cloud that already supplies the required occlusion, and
  overlapping clouds do not multiply opacity.
- Sibling teams may cover the throw and then bound through; the fire team
  remains the maneuver element and no fire-team player order is introduced.
- A squad whose direct sight is cut replans from its remaining belief: hold the
  smoke edge, reposition to a flank, continue a mission assignment, or use
  legal indirect fire. It may not continue direct firing at the last exact cell
  through the cloud.
- The use and response policies are faction-neutral. Enemy smoke becomes
  available only through explicit defender loadouts, but once present it obeys
  the same opacity and planner rules.

## Campaign and presentation integration

- Smoke occupies one billet's single special slot, has finite armory stock,
  and retains remaining grenade count in the battle snapshot only.
- Add a Maneuver/Screen template card and an explicit acquisition path in
  `s6-unlock-ladder-expansion.md`.
- The card and battle HUD identify the item as utility and show remaining
  throws; damage and anti-armor bars must not imply that it is a weak rocket.

## Boundaries

- No persistent weather, fire propagation, gas damage, wind simulation, or
  destructible-smoke interaction in the first slice.
- No command button that lets the player paint a grenade target. Squad AI uses
  the issued kit inside its existing mission order.
- No global accuracy debuff. A shot either has a legal direct line through the
  current opacity model or it does not.

## Acceptance

- Smoke lifecycle and occupied cells are deterministic under replay.
- Shared LoS, direct targeting, AI observation, player reveal, and roof reveal
  agree when smoke appears and expires, including for stationary observers.
- A focused open-lane scenario produces one smoke-supported crossing; a
  broken-squad scenario produces a smoke-supported withdrawal when and only
  when a usable escape path exists.
- Two equipped carriers do not waste grenades on one already-screened lane.
- Direct-fire AI stops shooting through opaque smoke while contact belief and
  legal indirect-fire behavior remain intact.
- Player and defender smoke obey identical simulation and planning rules.
- Armory stock, template cards, deployment freeze, HUD ammunition, and unlock
  reachability recognize the smoke special.
