# Convoy

Ground-vehicle reinforcement for the battle layer. Defender trucks
drive in along the road graph, deboard militia, and (eventually) take
fire on the way back out. The companion to the shuttle / air system.

## Contents

- [`v1-polish.md`](v1-polish.md) — what landed after V1: bicycle
  kinematics + pure pursuit, Reeds-Shepp docking, road reservation
  (buildings respect the road graph), convoy spawn diagnostics, debug
  toggles widget.
- [`stage2.md`](stage2.md) — Stage 2 design doc: Conquest reinforcement
  integration, multi-truck convoys, marine/truck interaction, damage
  model, vehicle variants, art replacement, driving-feel tuning.

## Related

- `roadmap/ai/` — battle AI roadmap (GOAP for infantry/mechs). Convoys
  spawn squads that hook into that planner once Stage 2 wires them
  into the commander loop.
- Memory: `[[ground_vehicle_kinematics]]`, `[[road_graph_design]]`,
  `[[render2d_batching]]`.
