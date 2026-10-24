# The Heart — where it came up

Status: PLANNED

Written: 2026-10-24

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

The mothers still stand at least twenty-two cells apart round the origin
(`heart-nouns.md`, Mother bore), so the origin is where the cluster centres,
and one mother, the first, opens at the telling object itself.

## Constraints

- Composed from what generation has: the places' growth and characters, the
  park, industrial-yard and medical-campus fillers, the layout's structures and
  blocks (`mission-layout.md`), wrecks and props. A new structure kind only
  where nothing fits.
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

1. The origin as a plan choice: `HeartCountry` picks it from the seed and
   gives the far place its growth and character.
2. Each origin's set piece in `HeartLayout`, named, so review frames box it.
3. The first mother seated at the set piece (`HeartBores`), the rest round it.
4. Look shots and the owner's pick; pool notes.
