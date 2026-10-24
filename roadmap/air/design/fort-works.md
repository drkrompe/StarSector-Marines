# Fort works — a fortification lot built in one style

Status: ACTIVE

Written: 2026-10-24

Read `ground-turret-tiers.md` (emplacement styles) and `tech-families.md` (the families) first;
`forts.md` is the fort these works belong to.

## What the owner asked for

Walls, barricades and other fortification cover in the same style families the ground turrets
use, so a fortification lot (a fort, a defence post, a fortified compound) reads as one built
place: its walls, gates, barricades, parapets, sandbags and gun emplacements all from one family.

## Vocabulary

- A **fortification lot** is the ground one set of works was built on: a defence post's layout
  cells, or a fort's whole footprint, belt included. A post inside a fort is part of the fort's lot.
- A lot's **works style** is the `EmplacementStyle` it was built in. It is the style of the lot's
  guns: one style per lot, and a post that stands alone is a lot of its own.
- The lot's **works** are the pieces drawn in that style: the perimeter wall, the parapet collar
  round a gun pad (`doodad.fort-emplacement-*`), and the barricade runs (the fort's
  `doodad.fort-sandbag-*` lines and a militia post's `doodad.sandbag-*` ring).
- A **look** is one piece's art in one style: a catalog entry named `<base id>@<style key>`, a
  doodad for a piece and a wall-faces block for a wall surface. One painted sheet per style holds
  its looks (`fortworks-<style>`).

## Laws

1. **Style is presentation only.** A styled piece keeps its catalog id, cover, catch band,
   footprint, blocking and damage family; a wall keeps its surface id, so every rule that reads
   a surface (Cordon's perimeter counts, say) reads the lot the same in any style. Only the
   picture comes from the look. A look's own cover fields are never read.
2. **A lot's style is its guns' style.** Generators mark the lot with the style they build its
   guns in (`EmplacementStylePolicy.forDefenders`). When a mission restyles a post
   (`EmplacementStylePolicy.restyle`, old works themed derelict), the whole lot the post stands
   in goes with it, so its parapets and walls never disagree with its guns.
3. **A missing look draws the base art.** Brutalist and earthwork are the base art (concrete
   parapets and walls, sandbag runs); a style draws its own look wherever it has one, and the
   base art where it does not, never nothing.
4. **Damage keeps the style.** A worn piece shows its damage state in the style it was dressed
   in, where that style has a look for the state.

## Flow

- **Generation marks lots.** `DefensePostStamper.stampPost` marks a post's layout cells and
  `FortStage` a fort's footprint, each with its style, on the topology's works-style channel
  (`CellTopology.getWorksStyle`).
- **The battle settles and dresses.** `BattleSimulation.setDefensePosts` brings each lot into
  its posts' style (`FortWorks.settle`: a flood over the lot's cells from the post's guns), and
  `addDoodad` dresses each piece standing on a lot in that lot's look (`FortWorks.dress`).
  `CoverPropDamage` dresses each damage state the same way.
- **Walls are dressed where they are drawn.** The ground pass draws a wall cell from its
  surface's look in the cell's works style (`GroundRenderSystem`), since its surface id is a
  fact rules read.

## Art

Each style's works are painted geometry-first in one ImageGen pass over one guide
(`art-source/fortworks/`): the collar plate of `fort-emplacement-guide.py`, a 3x3 barricade
ring whose eight cells are the eight run shapes, and the sixteen wall masks. The style's own
turret plinth is the material reference, so the walls are the guns' material. The exporter
measures every cut from the raw (ImageGen moves and rescales a little) and writes the sheet
through the authoring tools. One ring piece serves both barricade families.

| Style | Look |
|---|---|
| Brutalist | Board-formed pale concrete; precast barrier blocks for barricades |
| Earthwork | Sandbagged earth berms, gabion walls, timber revetting; sandbag barricades |
| Derelict | Riveted rusted colony steel with faded hazard striping (`derelict-colony-steel`); drafts `derelict-ai-war` and `derelict-pirate` are retained raws |
| Modular | League slate blue-grey pressed panels with strip lamps; modular barrier panels |
| Barrier | League earth-filled mesh barrier bins, tarps and cases |
| Export | Diktat sand-ochre ceramic-steel with red unit bands |
| Blockwork | Diktat interlocking orange-red and white moulded blocks |
| Composite | Tri-Tachyon pearl composite in hex plating with blue light lines |
| Deployed | Tri-Tachyon graphite pylons and pads with hard-light panes |

## Open

- The owner's pick between the three derelict drafts (fort works picks page).
- The damaged and destroyed states of every look (a worn piece shows the base art's damage until then).
- Pieces still drawn in the base art on every lot: the light post's vent ring (road-sheet
  frames with no catalog id), the fort's gatehouse and building shells, door and window
  openings, wall rubble, hedgehogs, trench and fence.
