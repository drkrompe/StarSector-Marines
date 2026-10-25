# The lanes' next tools

Status: IN PROGRESS — the fire-support section, shared spotting and the section's move-up are built on Broad Front; mortar teams are built but off (their rounds miss their mission); the rest wait on the owner decisions below.

Written: 2026-10-25

## Model

`heart-allied-forces.md` (what the allies bring), `heart-allied-commander.md`
(the commander choosing tools by need), `allied-tools.md` (the shed and its
catalogue) and `heart-bore-assault.md` (where each tool stands in an assault).
The laws hold: allies never end the battle, and the guns cannot take the heart.

## Why

Four Broad Front runs of the bore assault found its ceiling with the tools the
lanes have. Each gap below was the same in every run, whatever the go-in rule:

- **Nothing hurts a mother.** Both mothers ended every run at full integrity:
  each sits in the heart's facility building, so the ring of tanks and
  technicals has no line of fire to it, and every gun the lanes have fires
  direct.
- **The militia cannot win a mouth.** Every rule that sent them in more did
  worse; the strict rule did best because it seldom sent anyone.
- **The lanes break early.** Three of four broke at the same ticks (6,630,
  9,864, 12,889) in every run, and with them the militia an assault needs.
- **Seeing is the infantry's.** A ring that cannot see the bore cannot be told
  where to fire.

These are gaps in what the commander has, not in its rules. The fix is tools,
each with a place in the assault, not another round of tuning.

## What already exists

Most of the owner's list is in the code but not in the heart's forces:

- **Indirect fire:** `FIRE_SUPPORT_APC` (a mortar carrier) and `LRM_CARRIER`,
  both standing off beyond their target's reach (`Stance.STANDOFF`); field
  mortar sections, a squad halting to work a tube (`MortarSections`,
  `FieldMortarService`).
- **Spotting:** `SideObservers`, by which a soldier or mech calls a target for a
  launcher within 34 cells; Broad Front's forward observers, one per lane, who
  call the military's air wing.
- **Mechs:** Bulwark (the heavy variant), Warden and Hound; the heart sends only
  Hound lances.
- **A mobile scout:** `SCOUT_CAR`.

## The tools

Each is a tool in the lanes' shed, called up by need as the armour pairs are,
with a place in the bore assault.

1. **A fire-support section** (an LRM carrier and a mortar carrier). It stands
   on a ring behind the support line, out of the mouth's reach and out of the
   militia's way, and fires on the bore and its mouth without seeing them.
   Called for a lane whose ring has no line of fire to its bore, or whose bore
   has not lost integrity for a while. In the assault it is the base of fire's
   indirect half: it works the mouth before the militia go in.
2. **Spotters.** The lane's forward observer, and the leader of the squad on
   the support line, call targets for the section, as `SideObservers` already
   lets a soldier do for a launcher. A scout car on the ring is the lane's spotter
   where its militia are gone. The Burn's battlegroups get an observer each.
3. **Militia mortar teams.** One team in each wave carries a tube and works it
   from the support line as a mortar section; cheaper than a carrier, and lost
   with the lane.
4. **An assault team.** A team made for the mouth: satchels and close-quarters
   weapons, the team that goes in on a spent surge while the rest of the lane
   holds the support line and fires. Whether it is militia or the world's
   military is the owner's call.
5. **A Bulwark lance.** The heavy mech walks into the mouth with the assault
   team, where no hull should drive: it takes the surge the team cannot. Hound
   lances stay the town and city's screen.

## Owner decisions

1. **Which first.** Proposed: the fire-support section with spotters, because
   it answers the mothers nobody can hurt and is mostly existing pieces wired
   into the shed and the assault.
2. **Who the assault team is.** Militia with satchels, or a squad of the world's
   military attached to the lanes like the forward observers.
3. **Where tools come from.** The base's shed by need (as now), or with the
   waves on their timetable.
4. **Caps.** How many sections, teams and lances a battle may field.

## First check (answered from the code)

Yes on both. Both carriers' weapons are indirect fire, and an indirect-fire
launcher takes a target it cannot see when someone on its side within 34 cells
can (`GroundVehicleTurretSystem`, `SideObservers`). A bore is a hostile
turret-type structure, so it counts as hardened and even the LRM's hardened-only
mount takes it. Spotters therefore need no code of their own: the militia on
the support line, twenty cells from the bore, and the lane's forward observer
already call for the section whenever they can see the bore. The run's
carrier-seconds on a bore measure it.

## Built: the fire-support section

- The shed's `FIRE_SECTION` (an LRM carrier and a mortar carrier) comes up the
  supply road like the armour: one ready, at most two, restocked at the mech
  lances' pace (`FIRE_SECTIONS`, a default for decision 4).
- A lane on its bore calls one when none of its ring posts has a line of fire to
  the bore, or the bore has lost nothing for thirty seconds of base of fire.
- Its carriers stand as far out as their launchers reach (less a margin),
  facing the bore, and fire on what the lane's spotters see; never nearer than
  six cells behind the support line. Stood just behind the militia, the soft
  carriers were overrun within a minute or two of arriving. Broad Front only: The Burn's
  base sends trucks alone, and its battery is its indirect fire.
- Its carriers carry the bore as a fire mission: they shell it whenever anyone
  on our side sees it, and pick the nearest target only while nobody does.
  Without it a launcher took its own sight first, then the nearest, and was on
  the bore 4% of the time.
- Nobody calls fire while the swarm holds them: a marine or militia fighter
  dragged alive to a bore's mouth, or not yet up from being dropped, is no
  spotter, so a haul cannot bring rounds down on a bore early. (The player's
  sight bubble around a hauled body is the screen's fog only.)
- The summary reports sections called and carrier-seconds with a launcher on
  the bore.

## Built: spotting for friends

A launcher's spotters are its side's friends too (`Faction.friendlyTo`), not
its own faction alone: a marine calls a target for the militia's carriers, and
a militiaman for the marines'. Before, the company's marines, the only ones who
reach the mothers' building, could never call fire for the lanes.

## Built: the section moves up with its lane

Support fire moves with the front, it does not wait at the bore:

- The screened advance has a third element, the **support**, a line trailing
  the main that follows the formation and never holds it. A lane's section
  joins it as its carriers come up, and fires over the line on the bore the
  lane is going for, else on what the front sees.
- Between bores, where a lane goes straight into its next assault rather than
  a formation, the section trails the lane's militia: its ring is never nearer
  the bore than they stand plus its trail, and it takes its reach post once they
  are on the support line. A lane with no militia left sends it to its reach
  post behind the ring. Driving alone ahead of the militia, carriers were lost
  60 to 125 cells short of the bore.

## Built, off: militia mortar teams

Each Broad Front wave's first team carries a field mortar (its leader), marches
with the lane, and at a bore holds the support line with the mouth as its fire
mission (`battle.heart.mortarTeams`, off by default). In its first run 52 teams
fired 166 rounds and none on the mission: why their rounds miss the mouth is the
next thing to find before it is switched on.

## Acceptance

One Broad Front run per tool, read against the last (no tuning loops):

- the bore-assault summary: bores fallen to assaults, gone in and fallen back;
- both mothers below full integrity once the section is in;
- lanes broken later, or fewer;
- each tool's own counter (sections called, spotted shots, rounds on a bore).

## Out of scope

- The bore assault's rules (`heart-bore-assault.md`), which stay as they are.
- The base's guns and the Burn's barrage.
