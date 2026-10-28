# The Wellhollow Bloom

Status: ACTIVE — authored Rav Bloom arc on Asharu, the alpha campaign's end-game challenge; all four chapters are offered in a launched campaign; live acceptance remains.

Written: 2026-10-27

The authored instance of the Rav Bloom arc (`bloom-mission-arc.md`, *The
sector threat*): one named world, every Rav mission but Cordon Line in a fixed order, and a
narrative carried by the unlocks (owner, 2026-10-27: "missions unlock in their
prescribed order, but we have an overarching narrative with the unlocking
missions"). Every chapter stays replayable once it is unlocked. Cordon Line
is not a chapter in this release (owner, 2026-10-28): it and Cordon Road were
each half built, and the Road has the stronger hook, so the Road carries the
cordon beat alone. Read
`colony-breach-nouns.md`, `cordon-nouns.md` and `bloom-mission-arc.md` for the
battles; this doc owns only the arc: its order, gates, replay, narrative and
its boundary with the generic per-world arc. Voice follows
`swarm-lore-guide.md`.

## Premise, place and cast

Asharu is dust roads, salvage claims and sand farms. Its one green country is
**the Wellhollow**: a sunk basin over an old aquifer, wooded and irrigated on
the floor with the desert rim round it (`Basin.ravBloom`, the hybrid basin
every Rav map on an ARID world already carries). The aquifer is an old
seeding site, and this season it turned. "There's been a bloom on Asharu."

The bloom comes up all over the planet at once, and the work is the planetary
government's (owner, 2026-10-28). Asharu's settlers keep a small **Defence
Force** sized for pirates and tariff men, and a militia in every settlement;
the government (named in lines as the **Assembly**) hires outside outfits,
the company among several, to back them up. The arc's four jobs follow the
government's response:

1. **Caught off guard.** The Defence Force holds what it can (the water works,
   the port) and hires muscle for the population centres it cannot reach: the
   Wellhollow colony.
2. **Organising.** A defended line across the rim roads, with the farms walked
   out behind it, but people still beyond the wire, and the regulars committed
   to an offensive elsewhere. The company is hired for Low Sump.
3. **and 4. Taking it back.** The Defence Force turns to the Wellhollow, and
   the company fights attached to its brigade in the push on the channel root
   and then the heart.

- **Hollis Maro**, an irrigation-crew boss on the Wellhollow floor until the
  Defence Force called up its reserve and gave him the district. Hires and
  briefs the company for every job. Plain, tired, counts people, calls them
  **ravs** and the bores **holes**. His portrait still shows the farmer in a
  work jacket with militia webbing, which fits a reservist one week in.
- **Castle**, the company's operations voice, as in every arc.

Asharu in the base game is an independent world whose settlers keep no
government the Hegemony recognises; the Assembly and its Defence Force are this
arc's own and are not a game faction.

No line names the maker or says the weapon is gone (`swarm-lore-guide.md`, the
open maker rule). The arc ends with the Wellhollow's heart down and the
reservoir resting.

## Chapters, in order

Each chapter is one battle, the shipped mission it names, fought on Asharu's
ARID pool maps (`story-map-pools.md`). Winning a chapter unlocks the next.

| # | Chapter | Mission | Story beat | Fee |
| --- | --- | --- | --- | --- |
| 1 | **The Wellhollow** | Colony Breach | The colony on the basin floor erupts. Lift the people out; put the deep hole down if you can. It is not enough: the bloom is in the aquifer. | 20,000 |
| 2 | **Low Sump** | Cordon Road | The Defence Force's redoubts across the rim roads let the floor's farms walk out, but the line went round the pumping town at the basin's low end. Keep the gap open while the convoys go in and bring its people out. | 28,000 |
| 3 | **The Channel Root** | The Burn | The bloom has followed the irrigation channels east and seeded a second root. The Defence Force turns to the Wellhollow, and the company, attached to its brigade, burns in behind the barrage and collapses it. | 32,000 |
| 4 | **The Heart of the Wellhollow** | Broad Front | With the channel root down and the line held, every column the Defence Force has goes in together on the mother heart. Collapsing it ends the bloom; the reservoir rests. | 40,000 |

The order runs from the company hired alone to the whole Defence Force beside it, as
`bloom-mission-arc.md` (*The arc in one paragraph*) orders its movements: a
rescue, a road held open, a march behind the guns, then the all-in finale.
The Burn comes before Broad Front because the largest battle is the finale.
Both heart forms are played, which is the arc's departure from the generic
arc's one-variant-per-beat rule: here they are separate places on one world,
not alternatives.

## Unlocks

- **The arc opens** when Morrow departs at the end of A Working Ship, at
  Asharu, through Maro as a separate contact on the planet's mission
  board (owner, 2026-10-27). These are the alpha campaign's **challenge
  missions**: every chapter recommends seven squads and four mechs or vehicles
  (`MissionForceEnvelope.recommendedMachines`), a lieutenant colonel's company,
  and the board and briefing say so. Any ready force may still deploy.
- **A chapter unlocks the next on victory.** A loss leaves the same chapter on
  offer with a short retry briefing and no fee; nothing is consumed. There is
  no clock and no expiry, as in every authored arc.
- **The arc completes** when chapter 4 is won: Maro's closing briefing
  says the heart is down and the country can be taken back.

## Replay

Every chapter won stays on Maro's board as a **replay** next to the
current chapter. A replay:

- plays the same mission with a fresh vetted map from the chapter's ARID pool
  (attempt-indexed), so it is the same place on a different day;
- pays half the chapter fee on victory and nothing on a loss;
- carries no unlock, no completion and no one-time reward, and never rewrites
  the arc's progress;
- briefs with one replay line from Maro (the Defence Force still needs the
  ground walked) rather than the chapter's first briefing.

The debug mission player lists all four chapters under Asharu (WB1 to WB4,
`DebugMissionCatalog.Scenario.wellhollow`), each on its pool's first vetted
ARID map (`WellhollowBloomCampaign.fixedSeed`), so a chapter plays the same map
every time. It briefs with the chapter's first exchange and sets up the
challenge force: a Veteran Company of seven squads and four machines.

One attempt is active at a time; launch freezes it and only its own receipt
settles it, once (`early-operation-nouns.md`, the shared authored-operation
laws).

## Where the narrative lives

- **Briefings.** Each chapter's first briefing is a short exchange between
  Maro and Castle (`MissionDialogue` turns, strings under
  `wellhollow*` in `strings.json`); a retry and a replay each get one line.
- **The board.** Maro's offer rows carry the chapter name, its place in
  the arc ("Wellhollow 2 of 4") and the arc status (chapters won).
- **The debrief.** The results screen's story message closes each chapter
  with Maro's line on what changed (a chapter won) or what still stands
  (a loss).

- **The art.** Maro has a portrait
  (`art-source/portraits/hollis-maro.*`), and three location banners carry the
  arc's places (`StoryScene`): the colony floor for chapter 1, the rim cordon
  for chapter 2, the burning heart for chapters 3 and 4
  (`art-source/story-scenes/wellhollow-*`, Codex ImageGen); the rim banner
  now serves Low Sump alone.
  `createSnapshots -Psnapshot=wellhollow-art` shows them on Maro's board
  and the results screen. Briefing narration does not exist yet.

## Relation to the generic per-world arc

The generic arc (`bloom-mission-arc.md`, *Tie to the campaign*) runs on any
world `RavBloomSystem` blooms: three beats, the campaign picking each variant
from the last beat's outcome, campaign events under the one-open-event gate.
The Wellhollow Bloom is the authored instance its *sector threat* section
foresaw, built differently on purpose:

| | Generic arc | The Wellhollow Bloom |
| --- | --- | --- |
| Trigger | Any bloom the system wakes | Fixed: Asharu, after A Working Ship |
| Beats | Three; one variant each, chosen by outcome | Four chapters: the Road, then both heart forms, in fixed order |
| Source | Campaign events (`BLOOM_CORDON`, `BLOOM_HEART`, planned) | Story missions (`MissionSource.STORY`) with their own ledger |
| Gate | Holds the one-open-event gate while committed | Never touches the event gate, so random blooms keep calling |
| Clock | The bloom's stage clocks, with hold hooks | None; no expiry |
| Replay | None: a beat happens once per bloom | Every unlocked chapter replays |
| Outcome feed | Ledger facts carried beat to beat | Victory only unlocks; no carried facts yet |

The Wellhollow is not a `RavBloomSystem` bloom: Asharu carries no *Rav Bloom*
market condition and the system may still wake a bloom there by its own rules.
The launch code the arc adds (a story mission building the Cordon and heart
fixtures on a world's palette) is what the generic arc's factories will reuse.

## Open (owner picks pending)

Sent to the Rav Bloom coordinator for the picks page; the arc is built on the
recommended option of each.

1. **The middle beat.** Decided (owner, 2026-10-28): Cordon Line is cut
   from this release and Low Sump carries the cordon. A save that had won
   Hold the Rim counts it as Low Sump won.
2. **Replay pay.** Recommended: half the chapter fee. Alternatives: the full
   fee; a flat repeatable rate.
3. **When the arc opens.** Decided (owner, 2026-10-27): after A Working Ship,
   as end-of-alpha challenge missions.
4. **Art and voice.** Recommended and built: the Maro portrait and three
   location banners now, narration later. The art itself is on the picks page
   for review.

`stories.md` in this folder is the arc's open-work board.
