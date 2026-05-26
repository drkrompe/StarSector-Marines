# Convoy

Ground-vehicle reinforcement for the battle layer. The HEAVY_APC (sole
active variant — MILITIA_TRUCK retired) drives in along the road graph,
deboards a squad, then holds overwatch with a roof-mounted turret before
departing. Vehicles respect wall constraints — the footprint is checked
against the navigation grid each tick. The companion to the shuttle /
air system.

## Contents

- [`v1-polish.md`](v1-polish.md) — what landed after V1: bicycle
  kinematics + pure pursuit, Reeds-Shepp docking (CSC + CCC families),
  road reservation (buildings respect the road graph), wall constraint
  (footprint-check in advancePath), convoy spawn diagnostics, debug
  toggles widget.
- [`stage2.md`](stage2.md) — Stage 2 design doc: multi-truck convoys,
  marine/truck interaction, damage model, remaining vehicle variants,
  driving-feel tuning. Items 1 (Conquest integration), 6 (HEAVY_APC),
  and 7 (art replacement) are shipped.

## Related

- [`../reinforcement/`](../reinforcement/) — the orchestration layer
  above convoy. Convoy is one of three landed delivery means
  (walk-in, convoy, shuttle). Dispatch is resource-gated via
  `BattleResources` — compound-driven ticket production.
- `roadmap/ai/` — battle AI roadmap (GOAP for infantry/mechs). Convoys
  spawn squads that hook into that planner once Stage 2 wires them
  into the commander loop.
- Memory: `[[ground_vehicle_kinematics]]`, `[[road_graph_design]]`,
  `[[render2d_batching]]`, `[[air_unit_render_sync]]`.
