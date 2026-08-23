# 29 — Sirocco screened overwatch

**Status: implementation complete; awaiting integration.**

## Player-facing result

The Sirocco behaves as a medium-to-long-range brawler instead of a remote
artillery emplacement. When angling for a firing lane, it prefers to keep a
friendly combatant between itself and the threat. Another Sirocco does not
count as that screen, preventing a fragile all-Sirocco group from mistaking
itself for a front line.

## Contract

- Keep `OverwatchKillZoneGoal` and its MISSION priority. This is a refinement
  of the LR Support action, not a new goal or commander assignment.
- Search a 24–36 cell band around the known threat. The near edge gives the
  26-cell heavy cannon real shots of opportunity; the outer band retains the
  paired-LRM role.
- Prefer candidate cells whose candidate-to-threat segment contains a live,
  same-faction combatant. The screen must be meaningfully between both ends
  and close to that firing axis.
- A chassis with `MechVariant.SIROCCO` never satisfies the screen preference,
  regardless of its current doctrine role. Infantry, other mech variants, and
  ordinary allied combatants may.
- Screening is a preference rather than a hard precondition. If there is no
  screened firing lane, the Sirocco still takes the best valid overwatch
  lane instead of idling.
- Re-pick immediately when the cached screening ally dies or leaves the firing
  axis. Periodically re-check an unscreened perch so a newly formed friendly
  line can attract the Sirocco without requiring the enemy contact to move.
- Continue withholding non-installed/close missile behavior. The Sirocco's
  arms track may take a direct heavy-cannon shot in its band, while LRMs remain
  the long-band weapon.

## Verification

- A non-Sirocco ally between the candidate and threat wins over a comparable
  unscreened angle.
- Another Sirocco cannot provide the bonus.
- Enemy and off-axis units cannot provide the bonus.
- Losing the cached screen causes a re-pick.
- With no valid screen, the Sirocco still authors a firing route.
- Medium-band execution permits the installed heavy cannon to fire.

## Out of scope

- Hard bodyguard ownership or player-issued escort pairing.
- Predicting where a moving ally will be when the Sirocco arrives.
- Friendly-fire avoidance for LRM splash; that remains a weapon/target-scoring
  concern rather than a movement-screen rule.
- Commander-level lance formation and reserve placement.
