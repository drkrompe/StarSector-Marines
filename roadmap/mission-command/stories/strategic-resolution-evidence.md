Status: IN PROGRESS
Written: 2026-09-27
Updated: 2026-09-27 — source audit found full combat-grid allocations in navigation-only snapshots; compare compact storage independently.

# Strategic resolution evidence

The paced flank-budget capture records a32.4ms commander-topology rebuild.
`mission-command-nouns.md` is the canonical model. That full-resolution snapshot
is distinct from the existing8-cell influence blocks; changing a block-size
constant cannot by itself remove its copying and connectivity work.

Add disjoint rebuild wall stages and optional host CPU. Compare8-cell and16-cell
influence graphs/fields on identical frozen geometry and faction-local source
inputs after the timed replay. Account for physical propagation distance,
component connectivity and block-value aliasing across disconnected rooms.
Measure graph sizes, visited work, build/propagation timing and field differences.

Keep the live8-cell default, casualty route costs and precise navigation
unchanged. This is evidence infrastructure and a candidate-resolution test,
not adoption of coarse commander reachability or a combat-balance verdict.
Tests use tiny grids; large fixture evidence remains opt-in. Retire this story
once measurements identify whether/how to pursue the lower-resolution model.

The snapshot audit also identified unused cover/damage/opacity/barrier arrays
in command and asynchronous route snapshots. Use the existing routing-only
storage via an explicitly named API, preserving the generic full-copy contract.
Keep a same-build full-storage control; verify exact routes, geometry and frozen
ownership on tiny grids and measure the warmed copy stages. This reduces
storage, not navigation resolution or legality.
