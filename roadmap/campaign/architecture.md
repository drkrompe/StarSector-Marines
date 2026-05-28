# Campaign tier: architecture commitments

> Read this before writing any campaign-tier code. These are commitments
> we're making early so the system stays mutable as systems land. Each
> point names the cost of *not* honoring it.

## 1. Structure-of-arrays in primitive arrays

All persistent campaign-tier state lives in
[`CampaignState`](../../src/main/java/com/dillon/starsectormarines/campaign/CampaignState.java)
as parallel primitive arrays. One table = N parallel arrays + a count.

- **No boxed types in row data.** `int[]` not `Integer[]`. `long[]` not
  `List<Long>`. Strings get interned via
  [`IdRegistry`](../../src/main/java/com/dillon/starsectormarines/campaign/IdRegistry.java)
  before they enter SoA columns.
- **No `Map<Long, RowObject>`** as the primary row container. Row data
  lives in arrays; lookup *into* those arrays is a separate concern
  (see §4).
- **Soft delete only.** Setting `status = DORMANT` is how rows go away.
  Compaction is forbidden — it would shift indices and invalidate
  every cached lookup.

**Cost of not honoring:** boxing pressure, scattered allocations, GC
churn during the daily tick. Negligible at 700 houses but compounds
fast as state grows.

## 2. Behavior lives in Systems, not on the data class

[`CampaignState`](../../src/main/java/com/dillon/starsectormarines/campaign/CampaignState.java)
holds data + low-level mutators (`addHouse`, `addStake`, etc.). It does
*not* contain promotion logic, relationship rolls, chain advancement,
discovery propagation, or any other tick-time behavior.

Each tick-time behavior is a class implementing
[`CampaignSystem`](../../src/main/java/com/dillon/starsectormarines/campaign/CampaignSystem.java).

- **One system per phase.** Promotion, relationship interactions,
  chain advancement, garrison defaults, discovery propagation are
  five separate System classes — each independently testable, each
  independently swappable.
- **Systems are stateless behavior.** They never hold persistent
  fields. The `CampaignStateScript` holds the system list as
  `transient` — systems are reconstructed on every game load.
- **Systems read/write only `CampaignState`.** Talking to vanilla
  through `Global.getSector()` is fine for system inputs (clock day,
  market list); writing back to vanilla state is fine in the T3
  endgame system, nowhere else.

**Cost of not honoring:** behavior bleeds back into the data class,
systems can't be tested in isolation, the daily tick becomes one
1000-line god method.

## 3. Read/write declaration for future parallelization

Every System declares the tables it reads and the tables it writes via
[`CampaignTable`](../../src/main/java/com/dillon/starsectormarines/campaign/CampaignTable.java)
enum sets. The scheduler currently runs systems serially in order — but
the declaration lets a future scheduler:

- Run two read-only systems in parallel.
- Run two writers in parallel iff their write sets are disjoint AND
  neither reads what the other writes.
- Detect declaration bugs (a system writing a table it didn't declare)
  in debug builds.

We're not paying for the scheduler yet (single-threaded tick at our
scale is fine). We *are* paying for the declaration upfront because
retrofitting it across N systems later is the kind of refactor that
gets put off forever.

**Cost of not honoring:** no path to parallelism without rewriting
every system. At 700 houses we don't care; at 50,000 (mod-stacked sectors)
we'd care a lot.

## 4. O(1) lookup by id, both directions

Every primary key in `CampaignState` has an O(1) lookup path back to
its row index. Linear `for` scans over the table to find a row by id
are banned.

- **String ids** (`factionId`, `industryId`, `marketId`) round-trip
  through `IdRegistry`: `intern(string) → int`, `get(int) → string`.
  Slots persist across save/load.
- **Long ids** (`houseId`, `stakeId`, `chainId`) round-trip through
  [`LongIntMap`](../../src/main/java/com/dillon/starsectormarines/campaign/LongIntMap.java):
  `index(id) → rowIndex`, then `houseId[rowIndex]` to get the id back.
  Map maintained on every `addX`.
- **Sparse aligned lookups** (player rep row for a given house) use
  the same `LongIntMap` pattern.

Maps are persisted alongside the SoA tables — xstream walks them
transitively, the indices remain valid across save/load.

**Cost of not honoring:** at 700 houses, linear scan is fine. At 70,000
(stake table scaled with industry count) every tick walks 100M entries.

## 5. The dividing line for breaking these rules

- §1 (SoA / no boxing): break for one-off integration glue. Never in a
  per-tick hot path.
- §2 (Systems): break for the `CampaignDebugIntel` mutators — UI is
  allowed to poke state directly because it's not part of the
  simulation graph.
- §3 (Read/write declarations): never. The declaration cost is zero;
  the lock-in cost of not having it is unbounded.
- §4 (O(1) lookups): never for any operation that runs more than once
  per tick. Once-per-tick lookups can be linear if the table is small.

## Followups this doc gates

- [`contracts/overview.md`](contracts/overview.md) — contract resolution
  writes to `STAKES`, `HOUSES` (promotion progress), `PLAYER_REP`.
  Landed as `ContractLifecycleSystem`.
- [`infrastructure/overview.md`](infrastructure/overview.md) — buildings
  modulate `housePower` / default-rate math. Probably a passive modifier
  table read by multiple systems.
- [`t3-endgame/overview.md`](t3-endgame/overview.md) — the only System
  allowed to write back to vanilla state.
