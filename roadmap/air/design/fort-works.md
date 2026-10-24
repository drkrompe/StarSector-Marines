# Fort works — a fortification lot built in one style

Status: ACTIVE

Written: 2026-10-24
Updated: 2026-10-24 — the modular look's white trim is graded down to pale slate; fort buildings are kit buildings in the lot's style and the gate towers are rooms in the wall's; earlier: pirates build in their own patchwork style; derelict is colony steel

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
- **Fort buildings are kit buildings in the lot's style** (owner's pick). A fort building (fire
  control, magazine, power plant and their rooms) is an ordinary installation-kit building: its
  walls, doors and windows are drawn from the light installation wall in the lot's style, and it
  takes the ordinary liftable roof, opened like any other. Fort building art (`FortBuildingArt`)
  adds only the holder's banner and the blast door over the main door.
- **So is the gate.** The gate leaves are building art rather than a catalog piece: the renderer
  draws them in the works style of the fort's lot (`FortGatehouseArt.inStyle`), from the same
  file names in a folder named for the style, for every style the gatehouse's `looks` list names.
  The towers either side of it are rooms set into the wall (`GateTowers`, `forts.md`): their ring is
  the wall's, so it is the perimeter wall in the lot's style, and they take the ordinary roof.

## Art

Each style's works are painted geometry-first in one ImageGen pass over one guide
(`art-source/fortworks/`): the collar plate of `fort-emplacement-guide.py`, a 3x3 barricade
ring whose eight cells are the eight run shapes, and the sixteen wall masks. The style's own
turret plinth is the material reference, so the walls are the guns' material. The exporter
measures every cut from the raw (ImageGen moves and rescales a little) and writes the sheet
through the authoring tools. One ring piece serves both barricade families.

Each look's damaged and destroyed sheets (`fortworks-<style>-damaged`, `-destroyed`) are image
edits of its accepted intact sheet, cut with the intact sheet's measurements so every state
registers with the piece it replaces; they carry no wall tiles, since a broken wall cell is
rubble. Each intact sheet carries wall blocks for both the dark perimeter wall and the light
building wall, so a fort's buildings are its walls' material. The gate leaves of each style are
painted over the fort-buildings gatehouse guide with the
style's works sheet as its material reference (`generate.py <look> --gatehouse`,
`export_gatehouse.py`). Brutalist uses the base gatehouse.

A style may take a grade between its master and its cut (`art-source/fortworks/trim.py`), run
by every exporter of the style's art, so its walls, parapets, barricades, gate leaves and
turret pieces stay one material. Modular's near-white strip lamps and trim glared beside its
slate (owner), so bright unsaturated pixels are toned to a pale blue-grey at about two thirds
of their value; the masters stay as generated.

| Style | Look |
|---|---|
| Brutalist | Board-formed pale concrete; precast barrier blocks for barricades |
| Earthwork | Sandbagged earth berms, gabion walls, timber revetting; sandbag barricades |
| Derelict | Riveted rusted colony steel with faded hazard striping (`derelict-colony-steel`, the owner's pick); the `derelict-ai-war` draft is a retained raw |
| Patchwork | Pirate works: the colony steel patched with odd-coloured scrap plate, drums and tarps (`derelict-pirate`); its guns are the derelict guns |
| Modular | League slate blue-grey pressed panels with strip lamps; modular barrier panels |
| Barrier | League earth-filled mesh barrier bins, tarps and cases |
| Export | Diktat sand-ochre ceramic-steel with red unit bands |
| Blockwork | Diktat interlocking orange-red and white moulded blocks |
| Composite | Tri-Tachyon pearl composite in hex plating with blue light lines |
| Deployed | Tri-Tachyon graphite pylons and pads with hard-light panes |

## Open

- Pieces still drawn in the base art on every lot: the light post's vent ring (road-sheet
  frames with no catalog id), door and window openings, wall rubble, hedgehogs, trench and
  fence.
- Fort buildings and gate towers take the ordinary kit roof and get no roof of their own: roof
  styles are the kit-roof work's (owner), and a fort building wears whatever style that gives it.
