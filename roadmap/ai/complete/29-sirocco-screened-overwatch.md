# 29 — Sirocco screened overwatch

**Shipped 2026-08-22 in `98fcc3fb`.**

## Player-facing result

The Sirocco behaves as a medium-to-long-range brawler instead of a remote
artillery emplacement. When angling for a firing lane, it prefers to keep a
friendly combatant between itself and the threat. Another Sirocco does not
count as that screen, preventing a fragile all-Sirocco group from mistaking
itself for a front line.

## Shipped contract

- `OverwatchKillZoneGoal` retains its MISSION priority. This is a refinement
  of the LR Support action, not a new goal or commander assignment.
- `OverwatchKillZone` now searches a 24–36 cell band around the known threat.
  The near edge gives the 26-cell heavy cannon real shots of opportunity; the
  outer band retains the paired-LRM role.
- Candidate scoring strongly rewards a live, same-faction combatant lying
  meaningfully between the firing cell and threat inside a three-cell axis
  corridor.
- A chassis with `MechVariant.SIROCCO` never satisfies that preference,
  regardless of its current doctrine role. Other allied combatants may.
- Screening remains a preference rather than a hard precondition. With no
  screened firing lane, the Sirocco still takes its best valid overwatch lane.
- A cached screened lane re-picks immediately when its ally dies or leaves the
  axis. An unscreened perch checks again every two seconds, staggered by entity
  id so large debug lances do not all rescan on the same tick.
- Re-picking clears a route authored for the old perch before movement resumes.
- The arms firing seam now says what it does: `tryFireArms` fires the installed
  direct weapon, allowing Sirocco's heavy cannon at the inner edge while LRMs
  remain the long-band weapon. The old chaingun-named entry point remains as a
  compatibility delegate.
- Unversioned squad dumps expose `overwatchCellX`, `overwatchCellY`, and the
  live `overwatchScreenId` for playtest inspection.

The picker pre-resolves eligible ally positions once per search. Candidate
scoring then reads compact primitive arrays instead of repeatedly probing the
world, keeping the periodic search reasonable for the 100-mech debug stress
case.

## Verification

- `SiroccoScreenedOverwatchTest` covers infantry-screen preference, exclusion
  of a Sirocco even after a doctrine-role change, enemy exclusion, unscreened
  fallback, cached-screen invalidation, the 24–36 cell band, and a 25-cell
  heavy-cannon shot with LRMs withheld.
- The complete mech-focused test suite passed.
- Full `gradlew.bat build` passed with 2,044 tests before integration.

## Still out of scope

- Hard bodyguard ownership or player-issued escort pairing.
- Predicting where a moving ally will be when the Sirocco arrives.
- Friendly-fire avoidance for LRM splash; that remains a weapon/target-scoring
  concern rather than a movement-screen rule.
- Commander-level lance formation and reserve placement.
