# Raid: demolish the works, and a works worth assaulting

Status: IN PROGRESS. Built on the recommended options while the owner's picks
are open: `raid-demolition-targets` (a), `raid-demolition-fall` (a),
`raid-demolition-marker` (a), `raid-demolition-effort` (a). The first slice is
built (below).

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

## Decisions (owner's picks page)

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

### 2. The fall reads from above

A hall's roof hides its walls from the camera, so a hall half down still looks
whole (the fall pictures). When a hall is brought down its roof caves in over
the floor (`MapEditor.destroyRoof`, `RoofDamage.COLLAPSED`), showing the wrecked
machine inside. Decided by the objective, applied once through the map editor;
tested on the rule, not in a battle.

### 3. The marker

On the recommended look: the raid works family
(`MissionObjectiveFamilies.raidWorks`) draws each hall's footprint in place of
its machine's pocket circle, with the progress ring filled by the larger of the
charge and `hallProgress`. A hall brought down draws as wrecked. The objective
snapshot (`MissionObjectiveSnapshot.Item`) carries the hall's footprint.

### 4. Squads work a structure

On the recommended effort: a structure target joins target choice
(`TacticalScoring.findBestTarget`) as a candidate scored behind every enemy in
sight, so a squad with no enemy in sight and a hall in reach fires on its
nearest standing outline cell. Only weapons with wall damage
(`WeaponDef.wallDamage`) take it up; a rifle's few points are not worth a
magazine. The squad's own aim point is the cell, so the existing wall damage
path does the rest. Mission command gives the assault an order to work the
halls once the works is held (`mission-command-nouns.md`). The rule is proven
where the score is decided, by unit test.

### 5. A works worth assaulting

The owner: "We likely need to add a good amount of defenses still." Today a
works has its wall, gates, two to four emplacements and its own guard. Proposed,
scaled by security like everything else in `raid-mission.md`:

- **Open**: the works' fence, its guard rooms held.
- **Guarded**: adds wire and sandbagged firing points along the approach the
  landing faces, and a bunker at each gate.
- **Fortified**: adds a second gun per emplacement, a watch tower over each
  hall, and hard cover inside the works so a hall is fought for once the wall
  is passed.

Placed by the works' precinct like its emplacements (`PrecinctDefence`), named
with the works' place so the works' guard seats them first
(`RaidWorksGuard`). `storyMapPools` counts them in the Raid acceptance. This is
the one slice whose look goes back to the owner before it is built.

## Not in this story

- Demolishing buildings outside the works (a steal raid's stores, the
  garrison). The rule is general; whether to use it there is another pick.
- Rebalancing charge time against demolition. Balance is the owner's playtest.
