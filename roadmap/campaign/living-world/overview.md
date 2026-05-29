# Living world — the autonomous political simulation

> The autonomous half of [`../mechanics.md`](../mechanics.md). Where the
> four stub systems (`AutonomousPromotion`, `RelationshipInteraction`,
> `ChainAdvancement`, `DiscoveryPropagation`) finally do something, and
> the sector starts evolving without the player. Continues from
> [`../themes.md`](../themes.md) §"Sector-wide simulation".

## The gap this closes

The campaign tier today is a well-flavored **contract dispenser sitting
on a dead simulation**. `HouseSeeder` spawns one house per market and
*zero stakes*; the four autonomous `CampaignSystem`s have empty `tick()`
bodies. No house ever owns an industry, promotes, feuds, or falls. The
central promise of `themes.md` —

> *"Houses tick independently of player presence… sometimes the player
> returns to find the political map shifted."*

— does not happen at all. The sector is static scenery with good labels.

This thread makes it live.

## Two tempos of liveness

A world feels alive through **two distinct tempos**. Conflating them
produces either dead-static (today) or chaotic-noise. Keep them separate:

| Tempo | What moves | Player experience | Cost |
| --- | --- | --- | --- |
| **Drift** (continuous) | Industry shares creep ±1–2% per tick between rivals | Substrate. Felt only in aggregate, over months. | A linear scan; microseconds |
| **Events** (discrete) | A stake *flips* plurality; a house *promotes*; a house *falls* to `DORMANT` | The thing the player **notices** — the pull to go check on a planet. | A handful per month |

The discrete events sell "alive." The drift makes them feel *earned* — a
stake flips because months of creep finally crossed the plurality line,
not because an RNG fired this morning.

In the project's engine/game split ([[user-engine-game-framing]]): the
**two-tempo loop is the engine**; *which houses covet which industries*
is the game.

## The hybrid engine

Two coupled loops on the daily tick (the [`../mechanics.md`](../mechanics.md)
`onDailyTick` skeleton).

### Drift loop (weekly cadence — daily is too twitchy)

Each `ACTIVE` house with an ambition siphons a sliver of share from a
weaker *visible* rival on a contested industry. In byte-share space
(0–255) that's ~3–5 units a tick. A plurality is ~40/35, so an
*unattended* flip takes many months of creep. Drift can also claim from
the **unclaimed / faction-baseline remainder** — a house expanding into
open space, a gentler growth flavor than stealing from a rival.

### Chain loop (occasional — the headlines)

An ambitious house with no active chain and a coveted target spins up a
`chains[]` row with `patron == -1` (autonomous), archetype matching its
ambition, a threshold, and `discoveryRisk`. It advances slowly per tick;
on threshold → a **big** transfer (whole plurality / industry), a
promotion bump, an affinity tank between the two houses, and a discovery
event (see [The Chronicle](#the-chronicle)).

### The coupling (the part that matters)

**An active chain accelerates drift on its targeted industry.** By the
time the chain resolves, the share is already eroded — the seizure reads
as the culmination of visible pressure, not a teleport. This is the
`themes.md` Luddic-insurrection arc made mechanical: T1 (drift begins) →
T2 (chain visibly grows the cell) → T3 (resolution flips it).

## The decisive-accelerant principle (tempo balance)

`mechanics.md` already says player chains promote an aligned house *"by
amounts that would take months autonomously."* That is the rule that
keeps the player from being a bystander: **autonomous drift must be
glacial.** The world evolves on its own, but slowly enough that the
player's intervention is visibly the decisive force. A patron you back
climbs in weeks; left alone they'd take a year. The world lives — the
player *bends* it. This also protects player work from being casually
undone by background noise ([[feedback-hard-failure-preference]] wants
stakes to bite, not to be erased).

## Where the player plugs in — same rows, three ways

1. **Accelerant.** A player chain is a `chains[]` row with a real
   `patron`, fed by contract completions at a much bigger per-mission
   delta. Identical resolution path to the autonomous ones.
2. **Substrate.** Player stake-transfers on individual missions feed the
   same drift the NPCs swim in (the [`../mechanics.md`](../mechanics.md)
   `onMissionVictory` stake-transfer).
3. **Interventionist.** `discoveryRisk` on an autonomous chain can
   surface an intel ping — *"Korvath is moving on the Sindria
   refinery"* — and the threatened house becomes a counter-contract
   patron. The world acting on the player, then the player acting back
   ([[feedback-world-reactive-over-expressive]] in its purest form).

## The Chronicle

The output format of the entire living world: **a newspaper, not a quest
log.** The player watches the sector unfold the way you'd read dispatches
about a war on the other side of the planet — partial, second-hand, a
saga you're peripheral to. Four disciplines fall out:

1. **Partial and POV-limited.** Dispatches, not ground truth. Some arrive
   stale; some as rumor ("word is Korvath's been buying muscle…") that
   only later confirms. Never an omniscient feed — the same no-omniscient
   discipline the [narrative thread](../narrative/overview.md) commits to.
2. **Two bands, silent middle.** The player hears the **intimate**
   (houses they've touched — warm, callback-laden) and the **epic**
   (sector-shaking T3/T4 moves — news of a war in a far kingdom). The vast
   middle mutates silently, discoverable only on revisit. That gap *is*
   the "other side of the planet" feeling.
3. **Your own actions echo back.** The most satisfying loop in the
   system: a small thing the player did months ago returns as a headline
   about what it *became* — *"the refinery you took for Korvath last
   spring? they've parlayed it into the whole fuel monopoly."* The saga is
   partly theirs, and the game never says so out loud. Emergent
   discoverable-narrative ([[feedback-patron-narrative-discoverable]]).
4. **Reading the wire is a skill.** A headline that House Y lost its
   plurality is a *tell* — desperate houses overpay. The feed rewards the
   player who reads it instead of spamming the one who doesn't.

`DiscoveryPropagationSystem` is **the editor**: it decides what's
newsworthy enough to print, in which band, at what confidence. Headlines
are composed from sim data through the same token+voice machinery as
briefings ([`../narrative/overview.md`](../narrative/overview.md)), so the
procedural-fatigue discipline applies (variant pools per event type).

Storage TBD — likely a small append-only `chronicle[]` of *learned*
events (not all events), or reuse the intel feed. See open questions.

## Genesis (prerequisite for everything below)

The simulation needs a non-empty board. Two seeding fixes, both
deterministic from market id + seed (so a fresh game is reproducible;
the *running* sim then diverges and persists — no re-derivation on load):

- **2–4 houses per market** (the `themes.md` number), scaled by market
  size, so a market has a local rivalry. The T2-per-system house is their
  shared overlord, not the only player. *Without this, the literal
  premise — "House Drennar's stake transferred to House Korvath" — is
  unseedable: there's no Korvath on the same market to transfer to.*
- **Stakes seeded from each market's vanilla industries**
  (`market.getIndustries()`, readable in-sandbox). Each industry gets a
  deterministic dominant + contender among the local houses, plus an
  unclaimed remainder — instant contested pluralities for drift/chains to
  chew on.

## Slice spine

Each slice is independently observable.

| Slice | Lands | Observable result |
| --- | --- | --- |
| **A — Genesis** | 2–4 houses/market + deterministic stake seeding | Briefings get teeth: *"the refinery you're hitting is 40% House X, contested by House Y."* Pure data, no behavior. |
| **B — Player transfer** | `MissionResolver` victory → stake moves target→patron + promotion bump | The impact-ladder T1 rung is real; player actions leave permanent marks. World still static otherwise. |
| **C — Drift** | The breathing loop; `AutonomousPromotionSystem` live on real holdings | Shares creep; houses promote on their own. First "the world has a life." |
| **D — Chains + Chronicle** | NPC `chains[]`, big resolutions, `DiscoveryPropagation` printing dispatches | Full "map shifted while you were away" + the intervention hook. |
| **E — Consolidation + ambition** | `DORMANT`-on-empty, ambition re-eval, `CLAIM_THRONE` | Long-tail texture + the on-ramp to [`../t3-endgame/`](../t3-endgame/overview.md). |

A–B deliver real value with **zero** autonomous sim and directly unstub
the keystone. C–D are the living world. E is the long tail.

## Two payoffs that fall out for free

- **Consolidation endgame.** A zero-stake house → `DORMANT`, no respawn
  → the sector slowly crystallizes into fewer, bigger powers the player
  helped make or break. Emergent, not scripted.
- **`CLAIM_THRONE` seed.** A T3 house's ambition is the natural on-ramp
  to the faction-flip endgame — the autonomous loop produces Tier-4
  aspirants without special-casing.

## The ambition layer (the "game" half — least explored)

For any loop above to fire, houses need ambitions (currently always
`NONE`). Assignment is the behavior/content layer, and the difference
between houses that feel like opportunists vs. ones with real grudges:

- `CONSOLIDATE_STAKE` — wants plurality of an industry it already holds a
  minority in. The common case.
- `DISPLACE_RIVAL` — targets a specific rival house (a persistent grudge,
  not just opportunism).
- `PROMOTE` — near a rank threshold, pushes to climb.
- `CLAIM_THRONE` — T3+ eyeing T4.

Likely a mix of seeded long-term ambition (deterministic from holdings)
plus periodic re-evaluation as the board shifts (`themes.md` open-Q #4).
Designed in a follow-up once Genesis + drift exist to react against.

## Open questions

1. **Drift cadence + magnitude** — weekly at ±1–2%? Must stay glacial per
   the decisive-accelerant principle. Balance pass.
2. **Conservation** — drift is zero-sum between houses; chains may also
   claim from the unclaimed remainder (expansion vs. theft). Confirm.
3. **Vanilla industry coupling** — if vanilla disrupts/grows an industry,
   do our shares react? v1: ignore; fixed pie per industry at seed.
4. **Chronicle storage** — dedicated `chronicle[]` of learned events, or
   reuse the intel feed with a discriminator? Affects save size + the
   "actions echo back" lookback.
5. **Discovery surface** — how does an in-flight autonomous chain reach
   the player: intel ping gated by `discoveryRisk`, or only via a `SCOUT`
   captain / intel infrastructure (ties to
   [`../infrastructure/`](../infrastructure/overview.md))?
6. **House birth** — once consolidation thins markets, do new houses ever
   spawn, or is monotonic consolidation the intended long-game texture?
   (Leaning: let it consolidate.)
7. **Industry filtering at seed** — stake every industry incl.
   Population & Infrastructure, or only "economic" ones? v1: all.

## Related

- [`../mechanics.md`](../mechanics.md) — the data model + tick-loop
  skeleton this thread implements; stake-transfer + promotion math.
- [`../themes.md`](../themes.md) — the impact ladder, rank visibility,
  sector-wide-simulation framing.
- [`../narrative/overview.md`](../narrative/overview.md) — the voice
  machinery the Chronicle composes through.
- [`../contracts/overview.md`](../contracts/overview.md) — player chains
  feed contracts feed missions; the accelerant path.
- [`../t3-endgame/overview.md`](../t3-endgame/overview.md) — where
  `CLAIM_THRONE` ambitions lead.
- Memory: [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]],
  [[feedback-hard-failure-preference]], [[user-engine-game-framing]].
</content>
</invoke>
