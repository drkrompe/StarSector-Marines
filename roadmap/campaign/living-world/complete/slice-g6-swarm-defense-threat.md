# Slice G6 — swarm-defense threat payload

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `94cb765b`, `80020b48`, `6b386199`, `88421954`,
`710d2981`; playtest corrections `fb50b964`, `8b2af722`, `e0d29078`

## Goal

Replace the civilian-rescue battle's temporary conventional defenders with the
held biological swarm content while preserving the completed evacuation and
campaign-writeback contract.

## Shipped v1

- `Faction.DEFENDER` remains the opposed battle side. Append-only
  `UnitType.SWARM_RUNNER` and `UnitRole.SWARM_PRESSURE` provide threat identity
  without changing faction-wide win, targeting, rendering, or command rules.
- The runner reuses held `alien.png` and `alien-dead.png`; legacy `ALIEN`
  remains intact.
- Runners are fast close-contact attackers with no weapons, equipment, squad
  GOAP, ranged shots, conventional reinforcements, or fighter support.
- Target selection chooses the nearest registered active evacuee first, never
  scans ambient civilians, and falls back to the nearest live marine.
- LOW/MEDIUM/HIGH rescue missions install 12/24/40 deterministic runners on a
  complete set of reachable cells outside the shelter and lift zones.
- An incomplete placement aborts that map attempt before any partial roster can
  become player-visible.
- The dedicated factory no longer stamps defense-post turrets, allocates an
  Extraction defender roster, or installs reinforcement providers.
- Evacuation scaling, zero-economy mission terms, and hidden moral effects are
  unchanged.

## Verification checkpoint

- Focused tests cover append-only enum compatibility, sprite/stat identity,
  evacuee-first targeting, ambient exclusion, marine fallback, direct pathing,
  contact-only damage, all risk tiers, deterministic cells, protected zones,
  and all-or-nothing roster installation.
- The dedicated factory fixture confirms every opposed live unit is a runner
  and its reinforcement service is empty.
- `gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting was intentionally skipped for this session.

## Playtest correction

The first manual observation found runners stopping under sustained fire before
they reached contact. `SWARM_PRESSURE` had accidentally inherited the generic
25% on-hit fallback roll, replacing its pursuit behavior for 3.5 seconds even
though the payload contract made the swarm morale-free and implacable.
`fb50b964` exempts the role from legacy fallback. Regression coverage now proves
100 hit reactions cannot make a runner flinch and repeated pressure ticks close
the full distance into contact damage. The full Gradle build passes.

The debug personnel fixtures then exposed a second scale mismatch: the current
40-drop manifest can land roughly 300 marine-side personnel against the fixed
production roster of 12/24/40 runners. `8b2af722` introduced force scaling, but
its first version counted every later sortie cycle and spawned the entire enemy
budget at time zero. That produced an effectively instant civilian defeat.
`e0d29078` corrects the phase mismatch: debug rescue scales the initial roster
against simultaneous first-wave seats at 2:1 LOW, 3:1 MEDIUM, and 4:1 HIGH, and
stages runners at least 24 cells from the shelter. Later marine cycles remain
reinforcements rather than prepaid enemy strength. An eight-Valkyrie fixture
proves the battle and civilians survive the opening three seconds; explicit
large-roster tests and the full build pass. Production remains unchanged.

## Next

Close the player-facing outcome loop with a controlled zero/partial/full
battle-to-campaign fixture and explicit result presentation. Balance tuning
remains deferred until manual playtesting resumes.

## Opportunistic-targeting follow-up

`4fedb34a` supersedes v1's evacuee-first priority. Sensed marines and active
registered evacuees now share one distance-ranked pool, with 25% current-target
leeway to prevent oscillation. A substantially closer marine can peel a runner
off a colonist, and the resulting aggro change replaces the old path
immediately. Civilian discovery, ambient exclusion, strategic marine fallback,
and implacable contact pressure remain intact.

## Roving-reinforcement follow-up

`ad11debf` adds mission-local perimeter waves to rescue battles. Below 70% of
the opening runner population, a six-second cadence restores up to 25% without
exceeding the opening cap. Reachable entry cells remain outside protected zones,
at least 14 cells from active civilians and eight from marines; waves cease when
the rescue cohort is resolved. See
[`swarm-reinforcement-civilian-screen.md`](swarm-reinforcement-civilian-screen.md).
