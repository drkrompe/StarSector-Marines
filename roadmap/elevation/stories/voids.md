# Story — Voids: depth a map can't walk into

Status: PLANNED

Written: 2026-11-07

Updated: 2026-11-07 — named void; one battle rule under several void styles (crevasse, sea cliff, gorge river, city drop), outdoors as well as in cities.

Read `elevation-nouns.md` (tiers, faces, sight, look) and `surface-relief-nouns.md`
(macro relief, the sun) first.

## What the owner asked for

Urban maps lack verticality: a city's height is only its buildings, every
building is one height, and the generator gives city blocks no plateaus
(`elevation-nouns.md`, Generation). The owner wants to borrow the trick of
Star Wars RTS city maps (2026-11-07): **unwalkable space that implies height**.
The battle stays a flat grid, but the eye reads ground that falls away
several storeys below.

The owner named it **void** and wants it to serve more than cities
(2026-11-07): different **styles** for different needs, such as a crevasse, a
cliff, a deep river, and a skyscraper's edge over a futuristic city below.

The owner's MoonLightEngine technique of raymarching the tiles into a
virtualized 3D space is the fallback ("nuclear option") if the cheaper look
does not sell the depth. This story keeps it to the inside of a void (slice 5).

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
- A **bridge** is walkable ground across a void: ordinary ground or deck cells
  at the tier of the ground they join. Nothing walks under it, so "one cell, one
  level" holds. A bridge's width decides what crosses it, exactly as a ramp's
  does: infantry need one cell, a vehicle or mech needs its clearance.
- Roads over roads (overpasses) are **not** voids and are out of scope. They
  need two walkable levels on one cell, which this model forbids.

### Void styles

A **void style** says what a void looks like and where a generator puts it.
Every style plays the same in the battle. A style is presentation and
generation, never a battle rule, as an emplacement style is
(`ground-turret-tiers.md`).

| Style | Where | Edge | Floor | Bridge |
| --- | --- | --- | --- | --- |
| **Crevasse** | Ice and rock country: a narrow, jagged split | Blue-white ice or fractured rock, a snow or scree lip | Dark, narrowing to nothing | A snow bridge or a laid plank span |
| **Sea cliff** | A map's edge or a bluff: the land ends | Sheer rock or a cut quay wall | Surf and spray far below | None, or a causeway |
| **Gorge river** | A river cut deep, in place of a brook | Steep banks, rock and roots | Running water far below | A footbridge or road bridge |
| **City drop** | City blocks: a skyscraper's edge, a light well, the gap between arcology blocks | Poured concrete, ribbed steel, pipe runs, lit windows, a railing | A futuristic city far below: lights, haze, traffic | A deck span or skybridge |

A **sea cliff** is not an elevation cliff face. A face holds a plateau up
between two walkable tiers. A sea cliff drops from walkable ground to nothing
anyone stands on.

A **gorge river** is not the map's water. Water lies level with its bank
and its shore is walkable. A gorge river is a void whose floor is drawn as
water. Both are see-through and unwalkable, so they play alike.

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
  burst on and nobody there. A blast on the ground beside a void spreads over
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
  layout's own fields, in the way `CliffArtLayer` bakes an escarpment, in the
  void's style.
- **The floor.** The bottom is a deep layer in the void's style, drawn with
  more ground-plane parallax the deeper it is, so a drop shifts against the
  ground at its edge as the camera moves. Units never draw in a void and are
  never displaced (`surface-relief-nouns.md`, Laws).

## Slices

1. **Grid and battle.** The elevation map records void cells, their depth and
   their style. The brush stamps them with water's tactical tags and natural
   solid. Settle crashes and air drops over a void. A behaviour scene,
   `across-the-gap`: two squads either side of a void see and fire on each other
   while their routes go round by a bridge; a control with the void filled in
   as ground.
2. **Generation.** A **void** stroke in the elevation plan, naming its style,
   preflighted and proved like a plateau: it never covers roads, buildings,
   doorways, fixtures or reserved cells, and every place a host requires stays
   connected, through bridges where it must. `-Dbattle.mapgen.voids=N` asks on
   every map, for evidence, as `-Dbattle.mapgen.plateaus` does. Run
   `storyMapPools` after it. Each style's generator follows the table: city
   drops in city blocks, bridged where streets cross; crevasses in ice and rock
   country; a gorge river as a brook's alternative; a sea cliff along a map's
   edge.
3. **Look, first style.** Negative relief and its headroom, the baked edge and
   the parallax floor for the city drop, which started this.
   `-Dbattle.render.voidLook=false` is the control. Renders at 1920x1080
   through the look preset, for the owner's pick of edge and floor.
4. **Look, the other styles.** Crevasse, gorge river and sea cliff on the same
   bake, each shown to the owner.
5. **Raymarched depth (optional).** Only if slices 3 and 4 do not sell the
   depth: the composite raymarches inside void cells into a virtual space below
   (the owner's MoonLightEngine technique), reading the resident macro-relief
   field for the voids' walls. Bounded to void cells, where nothing stands, so
   the picture can never hide or show a unit the battle sees otherwise. Never
   applied to building faces, which would lean over units the battle still
   sees. Costed with `renderEvidence`.

## Laws

- A void is never walkable, never blocks sight or direct fire, and never
  changes in a battle.
- Every void style plays the same; a style changes only the look and where a
  generator puts it.
- Nothing stands, lands or falls in a void.
- Everything drawn inside a void is presentation. It neither displaces nor
  hides a unit.
- A map without voids behaves exactly as before.

## Open (owner)

- Depth: whole storeys (one to three) per style, or one "bottomless" look.
- Railings: does some edge give low cover against fire across the gap (a city
  drop's railing), or is every edge bare like a shore? A railing would be the
  one place a style touched the battle.

## Acceptance

- In `across-the-gap`, the squads trade fire across the void and route round it.
  With the void filled in as ground, they close.
- Generated maps with voids keep every required place connected
  (`garrisonEvidence`, `storyMapPools`).
- Renders of each style sent to the owner at 1920x1080. The edges and floor read
  as depth, with no mark painted on the ground.
