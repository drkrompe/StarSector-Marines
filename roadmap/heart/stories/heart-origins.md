# The Heart — where it came up

Status: ACTIVE

Written: 2026-10-24

Updated: 2026-10-24 — origins are real facilities broken open by the damage layer, not scattered story props (owner).

`heart-nouns.md` is the model, `heart-map-intent.md` the map direction and
`swarm-lore-guide.md` the lore this stays inside.

## Scope

Every heart map has its mothers round "the old reservoir workings". The owner
(2026-10-24): set design at the infection's root that tells, without words,
how the bloom got there, intentional or accidental, and a different story on
different maps.

The heart's far place, today always the workings, becomes the heart's
**origin**: one place per map, chosen per seed, the mothers standing round it
and the oldest growth thickest on the thing that tells the story. Each origin
is set dressing and ground, not text: no briefing line explains it, and a
player who walks in reads it.

The lore allows two ways in (`swarm-lore-guide.md`): the reservoir was under
the ground all along and the colony built on it, or dormant material rode in
on cargo with people who did not know they carried it. Proposed origins:

| Origin | How it got here | What the ground says |
| --- | --- | --- |
| **Workings** (today's) | Dug into it: the reservoir pumps went deep | Pump sheds, a capped shaft burst from below, the yard's pipes running down into the mother's mouth |
| **Freight yard** | Accident: it rode in on cargo | A container stack with one box split from inside, trucks stopped mid-unload, the yard's crane over the mouth; the growth trails from the box |
| **Research site** | Intentional: someone was studying it | A fenced compound, a sealed annex blown outward, the fence wired and sandbagged facing in; the mother rising from the annex |
| **Park** | Nobody's fault: the colony built over it | A green with paths, benches and a playground, a bandstand half swallowed; the mothers among ordinary things |

**A place, not a scatter** (owner, 2026-10-24): a first pass laid each
origin as a handful of story objects round the mother, with the creep painted
into the sprites, and read as "toyish": "We're not building up a larger ruined
facility for instance or a warehouse and having damage to break open walls /
rooms." So an origin is a whole facility built from the generator's ordinary
buildings, rooms and walls (a warehouse and its yard, a research compound, the
pump station, a park with its pavilion), and the story is told the way a ruin
tells it: by what is broken and from which side. The mother comes up inside
the building that matters, and the authored damage layer (`mapgen-nouns.md`,
Authored damage layers) breaks that building open from there: walls breached
outward round the core room, roofs fallen in a ring, the rest of the facility
battered by weeks of the swarm. A few props are accents inside it, never the
story on their own.

**The existing design language holds** (owner): the creep is the live creep
layer, drawn under every prop, spreading from the bores and withering when they
fall. No prop has creep painted into it.

The mothers still stand at least twenty-two cells apart round the origin
(`heart-nouns.md`, Mother bore), so the origin is where the cluster centres,
and the first mother opens in its core building.

## Constraints

- Composed from what generation has: fortress programs (buildings and rooms
  on a claim), the places' characters, the park, industrial-yard and
  medical-campus fillers, and the authored damage layer. A new program only
  where no existing one fits; props are accents.
- Every origin works on every form, biome and density; a city's origin is
  built-up ground, a rural one may stand in fields.
- The pools keep their seeds. The origin is drawn from the seed, so each pool's
  three seeds per biome should between them show at least two origins; the
  pool note names the origin.
- No tuning rides on it: the origin changes ground and props, not the bores'
  strength.

## Acceptance

- `storyMapPools` passes with each map's origin reported, and every origin
  appearing somewhere in the pools.
- A look shot per origin (the place on real ground, play zoom and close)
  goes to the owner as picture options before the set is fixed.
- The heart's evidence runs unchanged in kind: the mothers seat, the lanes
  reach them.

## Plan

Done: steps 1 to 3 as a first pass (`HeartOrigin`): three fenced facilities
from building programs, the first mother at the core building, its damage
layer. First look (`heart-origin-look`): the breach reads, but the freight
terminal and the research compound read alike, a fence round roofless boxes,
and the battering flattens every building to the same rubble. Next: damage
confined to the core so the rest keeps its roofs and fittings; each facility's
own ground (the freight yard's container rows from the props session's clean
tiles, the annex's inner fence); the park.

1. The origin as a plan choice: `HeartCountry` picks it from the seed and
   gives the far place a facility (a program or character) instead of the
   workings' sheds.
2. A damage plan per origin, centred on its core building, so the facility is
   broken open from where the mother came up.
3. The first mother seated in the core building (`HeartBores`), the rest round
   it.
4. Look shots of each facility and the owner's pick; the accents the props
   session keeps; pool notes.
