# Raid: demolish the works, and a works worth assaulting

Status: IN PROGRESS. The owner picked (a) on all four:
`raid-demolition-targets`, `raid-demolition-fall`, `raid-demolition-marker`,
`raid-demolition-effort`; and (a) on `raid-works-defences`, with the note
"Also consider things like vehicles and mechs". Slices 1 to 5 are built; slice
6 (the works' machines) is planned below.

Written: 2026-11-10

Read `raid-mission.md` in full first (Vocabulary, Break: wreck the works, The
owned map), then `raid-wreck-the-works.md` and `mission-command-nouns.md`
(Player and faction extensions).

## Goal

The owner (2026-11-10): "We likely need to add a good amount of defenses still.
We also need to figure out how the objective of raid destruction works. In
other games there is often a destruction mechanic which is the destruction of
the physical buildings and so on. Given we support per cell destruction we
could support his as well. We'd need to update the ai for soldiers to be able
to target structures or other destructables at some lower tier of effort and
update UI to indicate that as well."

Today a **break** raid wrecks the works' **machines** by charge: a settled
Marine holds the machine's pocket uncontested for the charge time. The halls
the machines stand in are scenery, though every wall cell of them already takes
damage through the map-structure channel (`MapEditor.damageWall`) from weapons,
charges, satchels and ordered breaching. This story makes the works' buildings
themselves the thing a raid brings down, and gives the works a defence that
makes that a fight.

## Vocabulary

Fits `raid-mission.md`'s nouns; nothing replaces the machine.

- A **hall** is the building a machine stands in. Two machines may share one.
- A hall's **outline** is the wall cells that enclose its floor, read once when
  the battle is set up (`BuildingStanding.outline`). Its **standing share** is
  the part of the outline not knocked down.
- A hall is **brought down** once its standing share falls to the line
  (`BuildingStanding.DEMOLISHED_AT`, half; pick `raid-demolition-fall`). A
  machine whose hall is brought down is **wrecked** with it, whatever brought
  it down. A charge still wrecks a machine too: demolition is a second way to
  the same wreck, not a new objective, so the share wrecked, the way out and
  what the raid is worth are unchanged.
- A **structure target** is a building the company may fire on for the
  objective (pick `raid-demolition-targets`). Firing on one is **working** it.

## Decisions (owner's picks page, all picked (a))

Pictures under `review/owned-maps/2026-11-10_raid-demolition_win/`, drawn on
the owned heavy-industry works; the fall pictures knock real wall cells down,
the marker and effort pictures are mock-ups.

- `raid-demolition-targets`: what counts as a target. (a) each machine's hall
  (recommended: the machine is still what the raid is worth, and the hall is
  how it can be reached by fire); (b) every building of the works, each adding
  to the share; (c) the halls plus the works' generator hall, which stops
  every machine at once.
- `raid-demolition-fall`: how much of a hall must fall. (a) half its outline
  (recommended); (b) three quarters; (c) its roof caved in and one breach.
- `raid-demolition-marker`: the marker. (a) the hall's footprint outlined with
  a ring that fills as it falls (recommended: the ring is the progress arc the
  machine markers already draw); (b) a bar and percent over the hall; (c) its
  standing wall cells lit and fallen ones dark.
- `raid-demolition-effort`: how much a squad gives a structure. (a) it fights
  first and turns on the hall only when no enemy is in sight (recommended);
  (b) riflemen fight while the heavy weapon and demolitions man keep working
  the hall; (c) never unless the player orders it.

## Slices

### 1. A brought-down hall wrecks its machine: BUILT

`BuildingStanding` measures a hall's outline and standing share; the Raid
setup finds each machine's hall (`BuildingStanding.outlineAt`) and gives the
objective the outlines (`RaidObjective.withHalls`). A machine whose hall is
brought down is wrecked with no charge, raises the alarm like a charge, and
opens the way out. `RaidObjective.hallProgress` is the marker's 0-to-1 reading.
Tests: `BuildingStandingTest`, `RaidWorksObjectiveTest`.

### 2. The fall reads from above: BUILT

A hall's roof hides its walls from the camera, so a hall half down still looks
whole. When a hall is brought down the objective tells the battle once
(`RaidObjective.withHalls`, its fall listener) and the Raid setup caves the
hall's roof in over its floor (`MapEditor.destroyRoof`, `RoofDamage.COLLAPSED`),
showing the wrecked machine inside. Test: `RaidWorksObjectiveTest`.

### 3. The marker: BUILT

The raid works family (`MissionObjectiveFamilies.raidWorks`) marks a machine in
a hall by the hall's floor as its footprint, in place of the pocket circle,
with a ring of its own (`MissionObjectiveSnapshot.Marker.progress`) filled by
`RaidObjective.siteProgress`: the charge or the hall's fall, whichever is
further along. Every standing hall shows its outline and ring, not only the
focused one (`MissionMarkerRenderSystem`). A machine in the open keeps its
pocket. Test: `MissionObjectiveFamiliesTest`.

### 4. Squads work a structure: BUILT

A squad of the raiding side with no wall of its own to open works the halls in
the ordered-breach pass (`WallBreachSystem`), which already spends only
wall-damage kit (launchers, satchels, mech mounts with wall damage) under every
gate the weapon has. It does so only while none of its members has a body to
fire at, so a structure ranks behind every enemy in sight; each member takes
the nearest standing outline cell of an unwrecked machine's hall within reach
(`WorksDemolition`). Rifles do not work walls. It is the squad's own reflex,
not an order: the player still commands the company. Test:
`WorksDemolitionTest`.

### 5. A works worth assaulting: BUILT

The owner: "We likely need to add a good amount of defenses still." Picked
(a), layered by security. `WorksDefences` lays them after the industry dresses
its works (`RaidWorksDressingStage`), by the raided market's security
(`PrecinctPlan.raidSecurity`). On an owned map that is the raided market's, not
the reference market the layout is laid against: like a sealed town, the
defences are the raided world's dressing and move no cell of the layout.

- **Open**: the wall and the works' own guard, as before.
- **Guarded**: a hedgehog belt on the works' glacis (the cleared ground its
  claim holds outside the wall, so the road that rings it stays clear) and a
  sandbagged checkpoint at each gate, inside the mouth where the gate opens
  straight onto a road.
- **Fortified**: also a watchtower in each corner of the works and a sandbag
  parapet round every machine hall, with a way through every few cells.

Every piece is a prop that yields to circulation (`WorksProps.place`), so a
corner or a parapet stretch with no room is left out. A military base lays its
own belt, checkpoints and towers at every security
(`RaidIndustry.laysOwnPerimeter`) and takes only the parapets. Test:
`WorksDefencesTest`; `storyMapPools`; the as-built pictures are
`raid-demolition-look`'s `built-*`.

## Not in this story

- Demolishing buildings outside the works (a steal raid's stores, the
  garrison). The rule is general; whether to use it there is another pick.
- Rebalancing charge time against demolition. Balance is the owner's playtest.
