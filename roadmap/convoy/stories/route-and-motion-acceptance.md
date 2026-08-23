# Convoy Route and Motion Acceptance

Status: READY — implementation is shipped; an eyes-on convoy run remains.

Written: 2026-08-23

Read `convoy-nouns.md` before running this story.

## Goal

Confirm that the shipped route, controller, recovery, docking, and delivery
layers read as one coherent heavy-vehicle journey in live play.

## Contract under test

- The road graph chooses a defender-side approach, interior drop-off, and exit;
  the cost field, vehicle clearance, and minimum-radius bend validation choose
  the route between them.
- Roads are visibly preferred, but a sensible open-ground shortcut is legal.
- Ordinary travel remains body-driven through continuous turns. Only the short,
  validated terminal docking maneuver uses pose playback.
- A blocked or changed-grid approach stops rather than pursuing a rejected raw
  corner, then recovers through committed reverse and, when necessary, a
  cumulative avoiding reroute. Genuinely no-route cases are recorded against
  `slice-3-recovery-ladder.md`, not accepted as terminally solved.
- The APC reaches the drop-off, unloads its four-person payload, holds armed
  overwatch, departs, and reaches the terminal gone state.

## Manual acceptance

1. Dispatch a normal defender convoy on a city route with at least one sharp
   corner, an available road-biased path, and a plausible open-ground shortcut.
2. Watch the complete off-map entry, inbound drive, final docking, unload,
   overwatch, outbound drive, and off-map departure.
3. Confirm turns are continuous and footprint-safe, the vehicle neither cuts
   through walls nor snaps between headings, and road preference does not act as
   a hard rail.
4. Exercise a blocked or kinematically awkward approach. Confirm recovery is a
   visible reverse or genuinely different, rate-limited reroute rather than
   repeated forward/reverse jitter. Treat a genuinely no-route case as evidence
   for `slice-3-recovery-ladder.md`, not a pass for terminal behavior.
5. Confirm the deboarded squad receives the reinforcement objective and the APC
   leaves no stranded live actor after departure.

## Acceptance

The full journey looks deliberate and every state transition agrees with
`convoy-nouns.md`. Record a correctness failure as a focused story. Feed purely
subjective handling observations into `slice-4-tuning-feel.md`; once the run
passes, fold and delete this story.

## Out of scope

- Multi-vehicle following and planner performance.
- Vehicle/infantry collision, vehicle damage, or new chassis variants.
- Pre-emptive tuning without an observed live-play problem.
