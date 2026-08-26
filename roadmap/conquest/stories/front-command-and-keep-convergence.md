# Conquest front command and keep convergence

Status: IN PROGRESS — implementation is complete; paired autonomous-command live acceptance remains.

Written: 2026-08-24

Updated: 2026-08-26 — added explicit attacker lane-line staging when only open-ground resistance is actionable.

Read `conquest-nouns.md`, `ai-nouns.md`, and
`autonomous-mission-command-foundation.md` before implementing this story.

## Intent

Keep the useful lateral organization of the Conquest advance without treating
track boundaries as exclusive ownership. Publish the front each track believes
it is serving, let idle squads support nearby resistance across a boundary, and
converge the assault force on the canonical keep when it becomes the remaining
territorial objective, or on a sole contested recapture after the keep falls.

## Scope

- Publish an immutable Conquest front snapshot with command phase, per-track
  assignment/strength, attacker progress, believed defender frontier,
  friendly/hostile pressure, and current target context.
- Retain a squad's sticky preferred track as an organizational default, but
  allow an uncommitted squad with no useful target in that track to support a
  neighboring track.
- Give an idle rear squad an explicit, reachable advance-track marker when
  commander belief sees resistance ahead but no discrete room can be assigned.
  Keep it behind the hostile front, bounded by friendly lead and safe stride,
  and yield to local contact without exposing a hostile identity.
- Enter an explicit keep-convergence phase when the canonical command post is
  the only uncaptured compound. Give every available assault squad useful
  approach or room-clear work across track boundaries while preserving
  distinct approach sectors where the keep geometry permits them.
- Enter final-compound convergence when the keep is held and one contested
  non-keep compound is the sole remaining objective. Preserve the capture quota
  and give every other mobile squad room-clear support across any track.
- Publish a per-squad command decision with preferred/effective track,
  assignment reason, target, and convergence state through the selected-squad
  panel and state dump.
- Keep compound-capture quotas and born-holding garrisons distinct from the
  mobile assault force.

## Acceptance

- [x] Put a squad just across a track boundary from the only defended reachable
  room in its neighboring track. If its preferred track has no useful target,
  confirm it receives a mission assignment instead of ambient Overwatch.
- [x] Leave useful resistance in a squad's preferred track and confirm the
  sticky track still wins rather than causing arbitrary lateral churn.
- [x] Put a contact-free squad behind an open-ground believed front. Confirm it
  receives a deterministic, reachable staging marker in its track, remains
  behind the believed hostile line, never moves backward, and publishes the
  order through the shared commander diagnostics.
- [x] Capture every supply compound except the canonical keep. Confirm command
  enters keep convergence and every non-garrison assault squad receives useful
  approach, clear, or capture work even when all keep rooms fall in one track.
- [x] Confirm track metrics and squad decision reasons are deterministic and
  identical between the selected-squad panel and JSON dump.
- [x] Confirm hostile progress and pressure consume only the marine commander's
  belief-derived influence snapshot; hidden defenders do not appear in the new
  front metrics.
- [x] Confirm existing compound quotas, capture preservation, strip preference,
  and full Conquest victory tests remain green.
- [x] Hold the keep, reopen one distant compound as contested, and confirm its
  capture quota remains deliberate while squads from nonadjacent tracks receive
  explicit final-compound room-clear work. An uncontested final compound keeps
  ordinary capture allocation.
- [ ] Run attacker and defender command together from first contact through
  compound progression and keep convergence. Confirm the assault progresses
  without player orders, defender patrols mobilize from their own reports,
  garrisons retain ownership, and neither snapshot leaks the other side's
  hidden state.
- [ ] During recapture and reinforcement, confirm free field squads remain
  strategically useful while garrison, delivery, and counterattack systems keep
  their authored force ownership.

## Constraints

- Tracks coordinate a front; they are not ownership fences and do not own map
  geometry.
- Keep convergence may redirect mobile assault squads but must not overwrite
  `HOLD_NODE` garrisons, morale survival behavior, or unrelated unit missions.
- A front snapshot is published command state. Presentation consumes it and
  never reconstructs assignments or hostile information from the live world.
- Other mission types require their own command geometry and stories; this
  story does not generalize Conquest tracks into a universal commander.

## Exit

Fold the durable track/front/convergence laws into `conquest-nouns.md`, add
this story to `shipped.md`, and delete it after live acceptance is complete.
