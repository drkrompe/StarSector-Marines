# Story — Urban voids: depth a city can't walk into

Status: PLANNED

Written: 2026-11-07

Read `elevation-nouns.md` (tiers, faces, sight, look) and `surface-relief-nouns.md`
(macro relief, the sun) first.

## What the owner asked for

Urban maps lack verticality: a city's height is only its buildings, every
building is one height, and the generator gives city blocks no plateaus
(`elevation-nouns.md`, Generation). The owner wants to borrow the trick of
Star Wars RTS city maps (2026-11-07): **unwalkable space that implies height**.
Streets and plazas stand at the edge of drops: light wells, undercity cuts and
the gaps between arcology blocks, crossed by bridges. The battle stays a flat
grid, but the eye reads a city several storeys deep.

The owner's MoonLightEngine technique of raymarching the tiles into a
virtualized 3D space is the fallback ("nuclear option") if the cheaper look
does not sell the depth. This story keeps it to the inside of a void (slice 4).

## Model

A **void** is the inverse of a plateau: ground **below** the map's tier, which
no ground unit stands on. It belongs in the elevation vocabulary next to tier,
face and ramp, and folds into `elevation-nouns.md` when it ships.

- A **void cell** is unwalkable for every ground unit and is permanent natural
  ground (`CellTag.NATURAL_SOLID`): wall damage, demolition, a wall-crushing
  hull and crashes never fill or open it. A void is fixed for the battle like
  every other part of the elevation map.
- A void has a **depth**, a whole number of storeys below the tier around it
  (the tier rise, 4 m). Depth is read only by the sun and the look. To the
  battle every void is simply a drop.
- A void's **edge** is its band of cells along walkable ground. It is drawn
  as a wall going down. To the battle it is the void itself, not a face, so it
  needs no width of its own.
- A **bridge** is walkable ground across a void: ordinary street or deck cells
  at the tier of the ground they join. Nothing walks under it, so "one cell, one
  level" holds. A bridge's width decides what crosses it, exactly as a ramp's
  does: infantry need one cell, a vehicle or mech needs its clearance.
- Roads over roads (overpasses) are **not** voids and are out of scope. They
  need two walkable levels on one cell, which this model forbids.

### Battle

Tactically a void is the inverse of a cliff. A cliff blocks movement and
blocks sight up it; a void blocks movement and **blocks no sight**: a gap you
can shoot across but not walk across. It reuses water's tactical profile
(`CellTag.SEE_THROUGH`, `CellTag.NO_EDGE_COVER`), so most of the battle already
honours it:

- **Movement.** Every ground authority already refuses an unwalkable cell
  (`elevation-nouns.md`, Movement). Routes go round, or over a bridge. The swarm
  does not cross a void and, like every ground unit, is never in one.
- **Sight and direct fire** pass over a void as they pass over water. The fog,
  AI perception and the fire ray agree because they already share the
  see-through rule.
- **Cover.** The edge gives none by default: a void is not a silhouette.
  (Open: railings on some edges, below.)
- **Indirect fire and grenades** that land in a void are lost: no ground to
  burst on and nobody there. A blast on the street beside a void spreads over
  it as over water.
- **Fire** does not spread into or across a void. It is not fuel.
- **Aircraft** fly over voids. A void cell is never a landing, drop or crash
  cell (a crash over a void lands at the nearest ground on its track, or is
  lost; settle in slice 1).
- **Nothing falls in.** No knockback, rout or vehicle slide puts a body into a
  void. Ground units never enter one, so there is nothing to fall.

### Look

The void is read from the ground, never from a UI mark (`elevation-nouns.md`,
Laws):

- **Depth in the relief.** A void cell stands its depth below the datum in the
  macro-relief field (`MacroReliefField`), so the battle's sun casts the near
  edge down into the drop with no shader change. The relief encoding holds 4 m
  below the datum today (`GroundHeightPass.MACRO_DATUM`): one storey. Deeper
  voids need that headroom raised, which costs height precision above the
  datum, or a depth clamp, under which the shadow still reads.
- **Baked edges.** The edge's wall going down is baked once per battle from the
  layout's own fields, in the way `CliffArtLayer` bakes an escarpment: an urban
  family (poured concrete, ribbed steel, pipe runs, lit windows), with a lip or
  railing along the street.
- **The floor.** The bottom is a deep layer drawn with more ground-plane
  parallax the deeper it is (undercity lights, haze, traffic far below), so a
  drop shifts against the street as the camera moves. Units never draw in a void
  and are never displaced (`surface-relief-nouns.md`, Laws).

## Slices

1. **Grid and battle.** The elevation map records void cells and their depth;
   the brush stamps them with water's tactical tags and natural solid. Settle
   crashes and air drops over a void. A behaviour scene, `across-the-gap`: two
   squads either side of a void see and fire on each other while their routes go
   round by a bridge; a control with the void filled as street.
2. **Generation.** A **void** stroke in the elevation plan, preflighted and
   proved like a plateau: it never covers roads, buildings, doorways, fixtures or
   reserved cells, and every place a host requires stays connected, through
   bridges where it must. City blocks may then carry voids: light wells inside
   blocks, cuts between them, bridged where streets cross.
   `-Dbattle.mapgen.voids=N` asks on every map, for evidence, as
   `-Dbattle.mapgen.plateaus` does. Run `storyMapPools` after it.
3. **Look.** Negative relief and its headroom, the baked urban edge, and the
   parallax floor. `-Dbattle.render.voidLook=false` is the control. Renders at
   1920x1080 through the look preset, for the owner's pick of edge family and
   floor.
4. **Raymarched depth (optional).** Only if slice 3 does not sell the depth:
   the composite raymarches inside void cells into a virtual undercity (the
   owner's MoonLightEngine technique), reading the resident macro-relief field
   for the voids' walls. Bounded to void cells, where nothing stands, so the
   picture can never hide or show a unit the battle sees otherwise. Never
   applied to building faces, which would lean over units the battle still
   sees. Costed with `renderEvidence`.

## Laws

- A void is never walkable, never blocks sight or direct fire, and never
  changes in a battle.
- Nothing stands, lands or falls in a void.
- Everything drawn inside a void is presentation. It neither displaces nor
  hides a unit.
- A map without voids behaves exactly as before.

## Open (owner)

- The name: void, well or drop.
- Depth: whole storeys (one to three) or one "bottomless" look.
- Railings: does some edge give low cover against fire across the gap, or is
  every edge bare like a shore?
- Outdoors: should ravines and canyons use voids too, or stay a city noun?

## Acceptance

- In `across-the-gap`, the squads trade fire across the void and route round it.
  With the void filled as street, they close.
- Generated city maps with voids keep every required place connected
  (`garrisonEvidence`, `storyMapPools`).
- Renders of slice 3 sent to the owner at 1920x1080. The edges and floor read
  as depth, with no mark painted on the ground.
