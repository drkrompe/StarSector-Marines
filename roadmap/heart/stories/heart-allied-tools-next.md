# The lanes' next tools

Status: PLANNED — design only; owner decisions below before building.

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

## First check before building

Whether a standing-off carrier fires on a target it cannot see but a friendly
spotter can, and whether a bore (a swarm structure) is a target it takes. If
either is no, that is the first piece of work, before any shed changes.

## Acceptance

One Broad Front run per tool, read against the last (no tuning loops):

- the bore-assault summary: bores fallen to assaults, gone in and fallen back;
- both mothers below full integrity once the section is in;
- lanes broken later, or fewer;
- each tool's own counter (sections called, spotted shots, rounds on a bore).

## Out of scope

- The bore assault's rules (`heart-bore-assault.md`), which stay as they are.
- The base's guns and the Burn's barrage.
