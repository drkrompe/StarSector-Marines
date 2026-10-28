# The Wellhollow Bloom

Status: ACTIVE — authored Rav Bloom arc on Asharu, the alpha campaign's end-game challenge; all five chapters are offered in a launched campaign; live acceptance remains.

Written: 2026-10-27

The authored instance of the Rav Bloom arc (`bloom-mission-arc.md`, *The
sector threat*): one named world, every Rav mission in a fixed order, and a
narrative carried by the unlocks (owner, 2026-10-27: "missions unlock in their
prescribed order, but we have an overarching narrative with the unlocking
missions"). Every chapter stays replayable once it is unlocked. Read
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

- **Warden Hollis Maro**, warden of the **Wellhollow Compact**, the irrigators'
  water compact and its militia. Speaks for the Wellhollow, not for Asharu or
  any faction; hires the company because nobody else is coming. Plain,
  tired, counts people, calls them **ravs** and the bores **holes**.
- **Castle**, the company's operations voice, as in every arc.

No line names the maker or says the weapon is gone (`swarm-lore-guide.md`, the
open maker rule). The arc ends with the Wellhollow's heart down and the
reservoir resting.

## Chapters, in order

Each chapter is one battle, the shipped mission it names, fought on Asharu's
ARID pool maps (`story-map-pools.md`). Winning a chapter unlocks the next.

| # | Chapter | Mission | Story beat | Fee |
| --- | --- | --- | --- | --- |
| 1 | **The Wellhollow** | Colony Breach | The colony on the basin floor erupts. Lift the people out; put the deep hole down if you can. It is not enough: the bloom is in the aquifer. | 20,000 |
| 2 | **Hold the Rim** | Cordon Line | The Compact throws redoubts across the rim roads. The company is the reserve while the floor's farms walk out behind the line. | 26,000 |
| 3 | **Low Sump** | Cordon Road | The pumping town at the basin's low end is cut off. Keep the gap open while the convoys go in and bring its people out. | 28,000 |
| 4 | **The Channel Root** | The Burn | The bloom has followed the irrigation channels east and seeded a second root. The Compact has guns but no men left: burn in behind the barrage and collapse it. | 32,000 |
| 5 | **The Heart of the Wellhollow** | Broad Front | With the channel root down and the line held, every column the Compact has goes in together on the mother heart. Collapsing it ends the bloom; the reservoir rests. | 40,000 |

The order runs from the company alone to the whole Compact beside it, as
`bloom-mission-arc.md` (*The arc in one paragraph*) orders its movements. The
Line comes before the Road because holding comes before reaching out; The Burn
comes before Broad Front because the largest battle is the finale. Both
Cordon variants and both heart forms are played, which is the arc's departure
from the generic arc's one-variant-per-beat rule: here the variants are
separate places on one world, not alternatives.

## Unlocks

- **The arc opens** when Morrow departs at the end of A Working Ship, at
  Asharu, through the Warden as a separate contact on the planet's mission
  board (owner, 2026-10-27). These are the alpha campaign's **challenge
  missions**: every chapter recommends seven squads and four mechs or vehicles
  (`MissionForceEnvelope.recommendedMachines`), a lieutenant colonel's company,
  and the board and briefing say so. Any ready force may still deploy.
- **A chapter unlocks the next on victory.** A loss leaves the same chapter on
  offer with a short retry briefing and no fee; nothing is consumed. There is
  no clock and no expiry, as in every authored arc.
- **The arc completes** when chapter 5 is won: the Warden's closing briefing
  says the heart is down and the country can be taken back.

## Replay

Every chapter won stays on the Warden's board as a **replay** next to the
current chapter. A replay:

- plays the same mission with a fresh vetted map from the chapter's ARID pool
  (attempt-indexed), so it is the same place on a different day;
- pays half the chapter fee on victory and nothing on a loss;
- carries no unlock, no completion and no one-time reward, and never rewrites
  the arc's progress;
- briefs with one replay line from the Warden (the Compact still needs the
  ground walked) rather than the chapter's first briefing.

One attempt is active at a time; launch freezes it and only its own receipt
settles it, once (`early-operation-nouns.md`, the shared authored-operation
laws).

## Where the narrative lives

- **Briefings.** Each chapter's first briefing is a short exchange between
  the Warden and Castle (`MissionDialogue` turns, strings under
  `wellhollow*` in `strings.json`); a retry and a replay each get one line.
- **The board.** The Warden's offer rows carry the chapter name, its place in
  the arc ("Wellhollow 2 of 5") and the arc status (chapters won).
- **The debrief.** The results screen's story message closes each chapter
  with the Warden's line on what changed (a chapter won) or what still stands
  (a loss).

- **The art.** The Warden has a portrait
  (`art-source/portraits/hollis-maro.*`), and three location banners carry the
  arc's places (`StoryScene`): the colony floor for chapter 1, the rim cordon
  for chapters 2 and 3, the burning heart for chapters 4 and 5
  (`art-source/story-scenes/wellhollow-*`, Codex ImageGen).
  `createSnapshots -Psnapshot=wellhollow-art` shows them on the Warden's board
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
| Beats | Three; one variant each, chosen by outcome | Five chapters; both variants of each, in fixed order |
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

1. **Order of the middle beats.** Recommended: Line before Road (hold, then
   reach out). Alternative: Road first (go back for the colony's people at
   once).
2. **Replay pay.** Recommended: half the chapter fee. Alternatives: the full
   fee; a flat repeatable rate.
3. **When the arc opens.** Decided (owner, 2026-10-27): after A Working Ship,
   as end-of-alpha challenge missions.
4. **Art and voice.** Recommended and built: the Warden portrait and three
   location banners now, narration later. The art itself is on the picks page
   for review.

`stories.md` in this folder is the arc's open-work board.
