# Black-swan events — the third content stream

**Status:** CIVILIAN-RESCUE EVACUATION-COHORT CONTRACT LOCKED
(2026-08-12); isolated battle tracker next.

**Implemented:** `cf8b717b`, `5fd8969d`, `1b2afcb4`, `0da1b89e`,
`34cf0654`, `5572c538`, `24ba5bdc`, `2a5461a6`, `0d49d30e`,
`fdfb0aef`

> Design discussion, not a spec. Continues from [`themes.md`](themes.md).
> The cadence/balance numbers here are intentions, not committed values.

Black-swan events are a **third orthogonal content stream** in the
campaign tier, distinct from Chains (multi-month political plots) and
Contracts (commercial agreements). Events deliver the gameplay weight
that doesn't fit the commercial layer — the moments where optimization
breaks down and the player has to decide *what kind of merc they are*.

| Stream | Cadence | Driver | Reward | Choice surface |
| --- | --- | --- | --- | --- |
| Chain | Months | Patron's political plot | Stake / power | Strategic alignment |
| Contract | Days–weeks | House-offered job | Cash + salvage | Tactical commitment |
| **Event** | Hours, unpredictable | World conditions / black-swan triggers | Unknown (often moral, not material) | Snap moral test |

## Why events matter for the mod's thesis

Starsector gives the player goals (don't go broke, get the best ship,
build a faction) but doesn't give them a *reason*. Events are how we
deliver the reason — situations where optimization doesn't apply, where
the player chooses based on *who they are* rather than *what pays*. They
are the densest [moral-compass](moral-compass.md)-touching content in the
campaign.

## Proof-of-concept: civilian rescue, swarm defense

There is already **held art for the first event content** — a
zergling-type swarm race. The PoC event:

- **Civilian rescue, swarm defense** — spend resources to help colonists
  escape offworld. A defense mission against swarms of the swarm race.
  Unknown rewards (maybe nothing material; maybe a captain's loyalty
  shift; maybe a moral-compass tick; maybe a piece of unique gear). The
  **cost-shaped** structure — you *spend*, you don't *earn* — is the
defining feature.

### Civilian-rescue foundation — locked v1

The campaign event and the eventual swarm-defense battle are separate slices.
The foundation ships a reusable choice/outcome lifecycle without requiring a
swarm faction, new AI, held art, or mission factory.

`campaignEvents[]` is a new append-only-identity SoA table. Each row persists:

| Column | Meaning |
| --- | --- |
| `eventId` | Stable internal identity |
| `eventType` | Append-only discriminator; v1 adds `CIVILIAN_RESCUE` |
| `eventTriggerKey` | Stable source identity; unique with type across retries |
| `eventState` | `PENDING_CHOICE`, `COMMITTED`, `REFUSED`, `RESOLVED`, or `EXPIRED` |
| `eventMarketId` | Registry slot of the endangered market |
| `eventCreatedTick` / `eventDeadlineTick` | Persisted choice window |
| `eventDecisionTick` / `eventResolvedTick` | Explicit choice/outcome days; `-1` until set |
| `eventSuppliesRequired` / `eventFuelRequired` | Exact cost-shaped commitment |
| `eventCiviliansAtRisk` / `eventCiviliansRescued` | Frozen stakes and explicit outcome |

The trigger boundary receives a stable trigger key and already-resolved market,
stakes, costs, and timing. Preparing the same `(type, triggerKey)` twice returns
the original event; it never creates duplicate pressure after save/load. The
foundation does not choose random cadence or inspect live markets itself.

Choice is deliberately sharp and always voluntary:

- A `PENDING_CHOICE` event may be explicitly refused at any time through its
  deadline. Refusal records `REFUSED` and a decision day; it consumes nothing.
- Acceptance requires an atomic resource port to commit the exact persisted
  supplies and fuel. Insufficient resources leave the row untouched. Success
  records `COMMITTED` and the decision day. There is no credit/salvage payout.
- An untouched pending choice expires only after its deadline. Expiry is not
  treated as an explicit refusal and has no moral effect.
- Only `COMMITTED` work can resolve. Resolution persists an explicit rescued
  count clamped to `0..civiliansAtRisk` and its day. Zero rescued is a real
  outcome fact, but mission failure alone does not manufacture a negative moral
  judgment.

### Hidden moral mapping — locked v1

The event appends through the shared source-unique moral ledger after its state
is terminal; it never exposes compass numbers or previews a moral reward.

| Persisted fact | Mercy | Stewardship | Integrity | Institutionalism |
| --- | ---: | ---: | ---: | ---: |
| Explicit refusal | -5 | -10 | 0 | 0 |
| Rescued 1–49% | +5 | +5 | 0 | 0 |
| Rescued 50–99% | +10 | +10 | 0 | 0 |
| Rescued 100% | +15 | +20 | 0 | 0 |

`CIVILIAN_RESCUE_REFUSED` and `CIVILIAN_RESCUE_SAVED` are distinct moral
source namespaces and use `eventId` as their source id. A committed zero-rescue
outcome records neither. Expired events record neither. This keeps failure,
inability, refusal, and successful protection as different persisted facts.

The foundation is shipped. Event identity, costs, stakes, choice windows, and
outcomes persist in the dedicated table; preparation is trigger-idempotent;
commitment consumes cargo atomically; passive expiry remains neutral; and the
daily moral consumer records only explicit refusal or a positive rescued count.
There is still no automatic event producer, intel/interaction surface, or swarm
battle payload. Those are deliberately separate follow-up slices.

### Trigger and choice surface — locked v1

Production and presentation are separate seams. `CivilianRescueSpawnSystem` is
a stateless daily `CampaignSystem` that may inspect vanilla markets but writes
only `CampaignState.EVENTS`. It never creates intel, consumes cargo, resolves a
mission, or touches the hidden compass.

The automatic schedule is deterministic and sector-global:

- The first eligible epoch starts on day 30; epochs are 45 days wide.
- At most one automatic rescue may exist per epoch. The epoch number is the
  automatic trigger key, so retries within the window return the first snapshot.
- No new event is prepared while any rescue is `PENDING_CHOICE` or `COMMITTED`.
  A terminal event permits a later epoch; it never permits a second event in the
  same epoch.
- There is no historical catch-up. On a late load/tick, only the current epoch
  is considered.
- Eligible markets are registered, visible vanilla markets with a primary
  entity and size 3+. The producer picks exactly one using a stable hash of
  `(epoch, market id)`, independent of economy iteration order.

The first content constants are deliberately simple and frozen into the row.
For `tier = max(1, marketSize - 2)`, the event costs `25 * tier` supplies and
`15 * tier` fuel, puts `100 * tier * tier` civilians at risk, and expires three
days after creation. Later balance changes affect new rows only.

`CivilianRescueIntel` is a single, always-registered **Distress Net** page. It
reads the newest active rescue from `CampaignState`, maps it to the endangered
market for location/context, and presents:

- market, civilians at risk, exact supplies/fuel commitment, and days remaining;
- **Commit relief stores**, routed through `CivilianRescueEvent.commit`;
- **Decline the call**, routed through `CivilianRescueEvent.refuse`;
- an explicit insufficient-cargo response that leaves the event untouched; and
- for `COMMITTED`, a truthful “relief committed; mission response pending” state.

The page never previews credits, salvage, unique rewards, moral dimensions, or
hidden deltas. It does not resolve committed work. A debug-only local spawn
button may call the same preparation seam with a reserved non-automatic trigger
key, but gets no alternate choice/outcome logic.

This vertical is shipped. The deterministic producer runs after passive expiry,
the Distress Net is registered on new and existing saves, choice buttons call
the shared atomic policy, and debug reachability reuses production terms in a
reserved trigger namespace. A committed call intentionally remains visible as
awaiting mission deployment; no placeholder victory or rescued count is
manufactured.

### Committed-event mission lineage — locked v1

The current generic `EXTRACTION` battle is an elimination placeholder. Its
winner and marine casualty count do **not** establish how many civilians
evacuated. G4 therefore lands the lineage and explicit-resolution contract
without making the mission player-facing until a battle payload can report a
real evacuation cohort.

The bridge uses dedicated fields, never contract semantics:

- `MissionSource.CAMPAIGN_EVENT` identifies black-swan battle work.
- `Mission.campaignEventId` carries the stable event id; `contractId` remains
  `-1`, payout/salvage remain zero, and no industry target is supplied.
- `Mission.campaignEventMarketId` carries the registry slot of the frozen event
  market; it is validated independently from player-facing planet text.
- `Mission.civiliansAtRisk` snapshots the row's stakes for briefing/battle
  setup. It must match the live committed row when the factory builds.
- `MissionOutcome` copies these fields and adds `civiliansRescued`. The sentinel
  `-1` means the battle supplied no valid evacuation report; it is not zero.
- Mission identity is `civilian-rescue:<eventId>`, parsed through a dedicated
  key type rather than inferred from display text or target planet.

`CivilianRescueMissionFactory` may build at most one immutable mission from a
local `COMMITTED` rescue row. The initial mission shape is `EXTRACTION`, zero
material reward, no employer support, and stable placement/content derived from
event id. Factory creation alone does not change event state.

Resolution is postcondition-first and replay-safe. It accepts only a
`CAMPAIGN_EVENT` outcome whose parsed mission key, explicit event field, target
market, and at-risk snapshot all match a still-`COMMITTED` civilian-rescue row.
`civiliansRescued` must be in `0..civiliansAtRisk`; a valid zero is an explicit
battle report. The bridge then calls `CivilianRescueEvent.resolve` once. Missing
reports (`-1`), stale/mismatched outcomes, generic victory, and marine losses do
not resolve or morally classify the event.

The future battle payload must report a representative evacuation cohort:
`initial`, `evacuated`, and terminal completion. Campaign rescued count is
`floor(atRisk * evacuated / initial)`, with exact full rescue when all cohort
members evacuate. Death, survival, and evacuation remain distinct; civilians
merely alive on the map at battle end are not automatically rescued. Until that
metric exists, committed calls remain mission-pending in Distress Net.

The G4 foundation is shipped. Existing mission constructors default safely to
no event lineage; committed rows build stable zero-economy mission snapshots;
and `MissionResolver` rejects event results before ordinary side effects unless
the strict bridge resolves an explicit report. The factory is not yet emitted
by `MarineOpsContext`, so the current placeholder battle remains unreachable
from Distress Net and cannot strand or misclassify an accepted rescue.

### Representative evacuation cohort — locked v1

The battle measures a small representative cohort instead of spawning every
campaign civilian. V1 uses eight registered evacuees. They are mission payload,
not the ambient civilians already placed around residential points of interest;
ambient deaths, survival, and escape never change the rescue report.

The battle tracker owns each registered entity through an append-only lifecycle:

- `ACTIVE` means the evacuee is still in play and has not reached safety.
- `EVACUATED` means the evacuee crossed the dedicated evacuation boundary.
- `LOST` means the evacuee died or became impossible to rescue.

Registration is identity-unique, and each transition is one-way and replay-safe.
Merely surviving the battle never transitions an evacuee to `EVACUATED`. The
tracker seals only at a terminal rescue decision; sealing converts every still-
active member to `LOST`, so an explicit zero remains distinguishable from a
missing or unfinished report. An empty cohort can never produce a valid report.

A sealed report contains `initial`, `evacuated`, and `lost`, with
`initial = evacuated + lost`. Campaign writeback remains
`floor(atRisk * evacuated / initial)`, calculated without integer overflow, and
returns exactly `atRisk` when the full cohort evacuates. Before sealing there is
no report (`-1`), even if the ordinary combat objective has picked a winner.

This contract hunk deliberately stops before entity spawning, movement, and
mission emission. The isolated tracker and report scaling land first; later
battle integration will give the evacuation zone sole authority to mark escape,
give casualty handling authority to mark loss, and seal at full evacuation or a
terminal impossible-rescue state.

## Design rules

- **Events fire from world conditions, not patron offers.** Triggers:
  random rolls, sector-level crises, specific chain side-effects, rare
  encounter generation.
- **Cost-shaped framing preferred** — events that *charge* the player
  rather than reward them. The reward is narrative / moral / unique-drop,
  not credits.
- **Unknown rewards** — events explicitly do NOT preview their payouts.
  The player commits blind; some events offer nothing material.
- **Feeds the [moral compass](moral-compass.md)** — accepting / refusing /
  the outcome of an event shapes the silent track.
- **Visual hook** — the held swarm-race art unlocks the swarm-defense
  archetype. Other archetypes can follow (sector plague, refugee fleet,
  abandoned colony with unknown threat, defector arriving with intel).

## Open questions (parked)

- **Cadence** — probably balanced to ~1 event per 30–60 in-game days,
  varying by sector activity.
- ~~**Refusal** — is "refuse to engage" always available?~~ Yes for the
  civilian-rescue v1; refusal is explicit and distinct from passive expiry.
- **Repeatable vs one-shot** — the civilian-rescue template can re-spawn;
  specific named/chain-tied events are one-shot.

## Implementation surfaces

- New System: `EventSpawnSystem` (a `CampaignSystem` impl) — per-tick rolls
  against world conditions to spawn event intel.
- Storage: dedicated `campaignEvents[]`; these choices are not commercial
  contracts and do not inherit contract payment/reputation semantics.
- Battle-tier: the swarm race needs its own `Faction` enum entry + unit
  types + AI behaviors. Significant infantry/AI work, and it needs the
  full-screen battle takeover style the mod is building toward (see
  [`../README.md`](../README.md) § Vision).

## Related

- [moral compass](moral-compass.md) — events are how the compass gets
  tested.
- [`themes.md`](themes.md) — the Chain/Contract streams events sit beside.
- [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]] — the durable principles
  events serve.
