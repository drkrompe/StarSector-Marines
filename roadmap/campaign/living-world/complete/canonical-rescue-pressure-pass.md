# Canonical rescue pressure pass — shipped

**Status:** CODE COMPLETE (2026-08-19)

## Outcome

- Canonical LOW/MEDIUM/HIGH rescue openings now contain 20/40/64 runners,
  replacing 12/24/40. The campaign rescue remains HIGH, so its opening force is
  64 runners.
- `SWARM_RUNNER` health is 2.5 HP. Service-grade fire now needs three pulse
  hits, four SMG hits, or one DMR hit; the generic alien remains at 1.875 HP.
- The DEBUG mission catalog now exposes `SWARM RESCUE — CANONICAL`. It mirrors
  the production HIGH risk, four-drop response budget, canonical runner count,
  and 16-cell opening approach band without campaign writeback.
- Existing LOW/MEDIUM/HIGH DEBUG rescue entries remain force-scaled stress
  tests. They still use 2:1/3:1/4:1 first-wave pressure and the wider 24-cell
  approach band.

## Verification

- Mission-catalog coverage locks the canonical row separately from all three
  stress-test rows.
- Roster tests lock the new counts and canonical/debug placement paths.
- Combat contracts lock the runner's 2.5-HP pulse/SMG/DMR breakpoints.
- Factory coverage verifies both the raised canonical HIGH population and the
  explicit 180-runner stress path.

Manual in-game cadence and difficulty validation remain queued.
