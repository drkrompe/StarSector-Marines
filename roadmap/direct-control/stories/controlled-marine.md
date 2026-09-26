# Controlled Marine slice

Status: IMPLEMENTED — focused and headless verification passed; live acceptance remains.

Written: 2026-09-23

Updated: 2026-09-26 — implementation is ready for the keyboard/mouse and UI ownership pass.

Read `direct-control-nouns.md`, `ai-nouns.md`, and `ui-nouns.md` first.
Uses the point-fire contract in `ballistics-nouns.md` and `direct-control-nouns.md`.

## Goal

Make one exact, already deployed Marine infantry member playable in the
standalone battle. This slice proves the interaction before hero eligibility
or a new campaign unlock is defined.

## Remaining acceptance

Run the live keyboard/mouse, camera, UI ownership, and lifecycle checks in
`direct-control-live-acceptance.md` for the Marine adapter. Entry currently
refuses terrain-overlapping AI poses and mission kit specialists, as defined
in `direct-control-nouns.md`. Broader shared clearance and manual mission
interactions remain outside this primary-control adapter.

## Implementation scope

1. Add a battle-owned one-body control session. Validate exact Marine identity
   on entry, consume immutable input at fixed ticks, and release through one
   handback path on explicit exit, loss of eligibility, or battle teardown.
2. Give the selected exact member an enter/exit affordance and mode/status
   readout. Route held WASD, mouse world aim, and primary trigger before world
   picking/orders/camera keys, while respecting retained UI input ownership.
   Follow the body with the battle camera and constrain active play to 1x.
3. Add an infantry direct-movement entry that preserves speed, normalized
   diagonal motion, body interaction, velocity, and animation. Sweep each
   proposed move across every crossed navigation edge, using
   `NavigationGrid.canTraverseCellStep` for reciprocal closed edges and
   diagonal corners; test the Marine's terrain clearance at sub-cell
   positions. Clamp at first contact and try only legal wall-tangent sliding.
   Apply actual displacement to the mover's velocity and gait.
   Manual execution must not skip cooldown or equipment housekeeping.
4. Feed primary fire through point-aim direct fire. Temporarily remove the
   controlled member from AI-assigned execution roles and replan the fire
   team/squad on entry and handback without changing mission assignment or
   roster membership. Use one availability rule for role assignment, arrival,
   stackup, and cohesion; keep living strength distinct from the autonomous
   formation population. A controlled squad leader must not remain the
   formation anchor or the sole writer of shared patrol/breach timers.
   Refresh retained assignments immediately on entry and exit.
5. Give manual aim precedence in the final facing pass without losing the
   movement gait. Bypass AI opportunity fire for the controlled member while
   retaining exactly one cooldown update. Cancel queued intent and point-burst
   continuation whenever manual fire is suspended or control is released.

## Acceptance

- Only an exact eligible Marine body enters; selection of a whole squad, an
  ally, enemy, incapacitated member, or riding member does not.
- Moving, aiming, and firing work while the rest of the squad continues its
  mission. The controlled member cannot walk through structure or fire faster
  than the equipped weapon allows. Another AI path or reflex never moves or
  fires the same member a second time in one tick.
- Direct motion stops at map bounds, solid cells, and a closed doorway or thin
  edge barrier between two walkable cells; it slides along a free wall side
  without cutting a blocked diagonal corner. Opening or destroying that
  barrier permits crossing on the next tick. Repeated held input and a frame
  containing multiple fixed ticks do not tunnel through it or leave stored
  movement to apply later. Terrain and body separation never push the Marine
  through a closed edge.
- Focused collision tests ask the movement rule directly about a wall, a
  closed shared edge, a blocked diagonal, a legal slide, changed topology,
  and a displacement long enough to cross more than one cell. A walkable
  cover feature stays traversable.
- Pause produces no movement or shots. Enter, exit, pointer-over-chrome,
  lost focus, death, boarding, withdrawal, battle completion, and detach all
  neutralize held input; normal camera pan, selection, orders, and AI resume.
- A headless controlled scene records movement, fire, squad progress, and
  handback from the same fixed-tick intent sequence twice with identical
  results. A live pass checks keyboard/mouse feel and UI ownership.
