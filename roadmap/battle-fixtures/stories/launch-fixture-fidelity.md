# Launch fixture fidelity

Status: PROPOSED

Written: 2026-08-24

## Outcome

A captured launch can reconstruct the production battle as it exists after the
briefing path applies campaign-owned forces and capabilities, not only the
scenario factory's tick-zero defaults.

## Scope

- Define a higher-level frozen launch specification around the existing
  campaign-to-battle overlay inputs.
- Replay persistent marine identities, equipment, fighter cover, command
  powers, and command-power resources through the same production seams used by
  `MissionLaunch`.
- Extend capture and deterministic headless comparison without serializing
  mutable simulation/ECS state.
- Decide whether campaign references become stable value snapshots or fixture
  lookups, preserving campaign-free battle construction.

## Constraints

- The V1 scenario fixture remains the lower construction layer.
- No test-only deployment, loadout, flyby, or command-power builder.
- A launch fixture still reconstructs tick zero; command/input replay and
  mid-battle checkpoints are separate stories if they become necessary.

## Acceptance

- One captured production-shaped launch rebuilds the same initial personnel,
  loadouts, aircraft support, powers, and resource counts headlessly.
- Changing an overlay in its production seam changes launch replay without a
  parallel fixture implementation update.
- Older V1 construction fixtures continue to load and replay.
