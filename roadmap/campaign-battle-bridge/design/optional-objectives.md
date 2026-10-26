# Optional objectives

Status: ACTIVE — the mission-neutral mechanism is built (roll, board, beats,
campaign hand-off). The Cordon rolls its pool (`cordon-nouns.md`, Optional
objectives); the heart (`heart-story-beats.md`) is next.

Written: 2026-10-23

## What it is

A mission may carry a **pool** of optional objectives: side goals beside its
main objective, each with a reward when it succeeds and a cost when it fails.
A battle does not play the whole pool. It **rolls** a few of them against the
map it actually got, runs them beside its story, and hands what came of them to
the campaign. One mechanism serves every mission that wants side goals; a
mission brings only its pool, a beat prefix (`cordon.`, `heart.`) and the call
that advances the board from its own story sequence.

## Nouns

- **Optional objective** — one entry in a pool: a stable key, a title and a
  briefing line, the structures it **needs**, the keys it **excludes**, a
  roll **weight**, a **reward**, a **cost**, and how to **start** its runtime.
- **Needs** — names of pieces in the map's laid layout (`LaidLayout`). An
  objective fits a map when every piece it names stands on it, whole or in
  part (`LAID` or `PARTIAL`); a refused or missing piece means it does not
  fit and is never rolled there. An objective that needs nothing fits every
  map. This is what ties side goals to what generation really placed: a beat
  about a wreck is only offered when the wreck was laid.
- **Excludes** — keys never drawn in the same battle, checked both ways, so
  two beats that tell contradicting stories (or fight over one structure)
  cannot meet.
- **Roll** — the draw of a battle's set from a pool: filter by fit, pick a
  count in the mission's range clamped to what fits, then draw by weight
  without replacement, honouring excludes. It is a pure function of the pool,
  the laid layout, a seed and the range, drawn from its own salted random and
  never the simulation's, so the briefing and the battle it describes roll the
  same set and rolling never moves the battle.
- **Board** — a battle's rolled objectives while they run. Started at setup,
  advanced serially by the mission's story sequence, ended when the battle
  ends.
- **Runtime** — one rolled objective's battle-local state. Each advance it
  reports **active**, **succeeded**, **failed** or **lapsed**; the first final
  report sticks. At battle end anything still active is asked how it ends, and
  lapses unless it says otherwise.
- **Lapsed** — the trigger never came (the convoy the beat was about never
  ran). Neither reward nor cost: the player was never tested.
- **Reward** — credits, materials (commodity ids paid as cargo) and
  **grants** (keyed things the campaign arc interprets, such as standing).
- **Cost** — a named difficulty modifier with a size, carried downstream for
  the arc to apply to later missions.
- **Result** — what the campaign receives per rolled objective: key, title,
  final status and the declared reward and cost. Only a success earns the
  reward; only a failure incurs the cost.

## Flow

1. **Setup.** The mission rolls its pool against the laid layout and the
   battle seed, builds the board over the battle (its beat log, layout, seed
   and prefix), starts it, and installs it on the battle.
2. **Battle.** The mission's story sequence advances the board each tick it
   advances itself. Every objective publishes
   `<prefix>objective_<key>_started` when it starts and one of `_succeeded`,
   `_failed` or `_lapsed` when it goes final — ordinary player beats through
   the battle's story beat log, so radio, narration and evidence read them as
   they read every other beat.
3. **Hand-off.** When the outcome is computed the resolver ends the board and
   copies its results onto the mission outcome, in roll order.
4. **Settlement.** Credits of succeeded objectives are paid with the payout;
   their materials join the recovery manifest as field finds do (claiming no
   salvage budget), whether or not the battle was won — the objective is its
   own fact. Grants and costs are only recorded on the outcome.
5. **Debrief.** The results screen shows one line per rolled objective: its
   title, its status, and what it paid or what it costs.

## Laws

- **Fit is read off what was laid, never off what was planned.** A pool may
  name any structure; the map decides which beats are possible.
- **Same inputs, same set.** Nothing in the roll reads the simulation, the
  clock or a shared random.
- **Results are final once.** A runtime cannot leave a final status; ending
  the board twice changes nothing.
- **The mechanism interprets no grant and no cost.** Their meaning belongs to
  the arc that receives them; the outcome is the seam it reads.

A mission's authored side jobs are the sibling noun, side objectives
(`mission-command-nouns.md`): declared, not rolled, with an Optional checklist
section and campaign marks of their own.

## Open

- The heart's pool, count range and runtimes (`heart-mission-depth.md`).
- The arc's reading of grants and costs.
- Showing the rolled set in the briefing, from the same roll.
