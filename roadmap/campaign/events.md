# Black-swan events — the third content stream

**Status:** CIVILIAN-RESCUE OUTCOME CLOSURE CODE COMPLETE (2026-08-12).

**Implemented:** `cf8b717b`, `5fd8969d`, `1b2afcb4`, `0da1b89e`,
`34cf0654`, `5572c538`, `24ba5bdc`, `2a5461a6`, `0d49d30e`,
`fdfb0aef`, `cc34a2ab`, `1174cae9`, `03017229`, `a2fa2a70`,
`5d6257f5`, `c870193f`, `d1dae861`, `2cd8416a`, `bee0c6b4`,
`84e9e175`, `94cb765b`, `80020b48`, `6b386199`, `88421954`,
`710d2981`, `38bc6323`, `a27064fc`, `cf442e11`, `9e0417aa`

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
reserved trigger namespace. A committed call directs the player to Marine
Operations at the frozen market, where a local Distress Net client exposes the
single lineage-bound rescue mission.

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
the strict bridge resolves an explicit report. G5 now emits that factory only
through the matching market's synthetic Distress Net client and launches it
through a dedicated evacuation battle instead of generic Extraction.

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
tracker seals only at a terminal rescue decision and only after the full expected
cohort has registered. Sealing converts every still-active member to `LOST`, so
an explicit zero remains distinguishable from a missing, incomplete, or
unfinished report.

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

The G5 foundation is shipped. Each battle simulation owns an empty eight-member
tracker; rescue fixtures may register identity-unique representatives and apply
replay-safe evacuated/lost transitions. Only a fully registered, sealed tracker
produces an immutable report, and sealing accounts every survivor still outside
the evacuation boundary as lost. `MissionResolver` reads that report only for a
campaign-event mission, scales it to the frozen stakes using `long` arithmetic,
and preserves `-1` for unfinished work and `0` for a measured zero rescue.

The controlled objective slice is also shipped. `CivilianEvacuationObjective`
walks only registered identities, counts inclusive lift-zone entry, marks absent
members lost, completes on a positive partial/full rescue, and fails on a
measured zero. Ambient civilians at the same cell remain irrelevant. The shared
death mailbox also records registered casualties, and every battle-terminal
path seals a complete cohort before Results reads it.

The production payload is shipped. Placement deterministically chooses a
residential interior, eight unique reachable shelter cells, and a distant
reachable outer-band lift; up to eight deterministic map attempts prevent a
partial payload from becoming visible. The installer spawns a fixed civilian /
engineer / scientist mix as mission-only squadless VIPs, registers the complete
cohort, and attaches the evacuation objective.

A serial evacuation system owns their paths. It routes each active member to
the lift, boards and removes them from live rendering on boundary entry without
creating a corpse, and leaves death/loss to the shared death mailbox. Mission
launch recognizes `CAMPAIGN_EVENT` and builds this battle; generic Extraction
is unchanged. The matching market alone exposes one committed mission through
an always-open Distress Net client. Swarm faction, roster, AI, and held art are
the next content slice.

### Swarm-defense threat — shipped v1

`Faction` remains the battle-side abstraction (`MARINE`, `DEFENDER`,
`CIVILIAN`). V1 therefore represents the swarm as append-only unit and role
identity on the `DEFENDER` side, not as a fourth faction that would silently
rewrite two-sided win checks, fog colors, targeting, reinforcement, and command
assumptions.

The held `alien.png` seven-frame live sheet and `alien-dead.png` four-pose corpse
sheet originally supplied the new `SWARM_RUNNER` `UnitType`. The existing generic
`ALIEN` value stays intact for compatibility. The runner is a fast, close-range
biological attacker with no weapon, squad GOAP, morale, equipment, reinforcement,
or ranged projectile semantics. Live aliens now prefer a generated true-overhead
body/head/foot composition on the marine layered runtime; the held sheets remain
the all-or-nothing load fallback and corpse source (`bccbbe16`).

A dedicated `SWARM_PRESSURE` role owns its behavior:

- rank sensed marines and registered `ACTIVE` mission evacuees in one
  distance-based candidate pool, so category never overrides a clearly closer
  target;
- keep the current victim while it remains within 25% of the best candidate,
  preventing oscillation while allowing a substantially closer marine to peel
  pressure away from a colonist;
- remember valid discovered prey after line of sight breaks, but never receive
  the cohort's position before first contact;
- never choose an ambient civilian merely because it shares `Faction.CIVILIAN`;
- when no local target is sensed, continue toward remembered prey or pressure
  the nearest live marine as a strategic fallback;
- reroute immediately when aggro changes instead of waiting for the ordinary
  movement repath throttle;
- path directly toward the chosen target and apply contact damage only from an
  adjacent cell; and
- idle if neither a registered evacuee nor marine exists.

The first roster is mission-local and risk-scaled, with no militia, regulars,
mechs, turrets, conventional reinforcements, or fighter support. It spawns only
on complete reachable cells at least 16 Manhattan cells from the shelter and
outside the lift zone. All eight evacuees spawn strictly inside the chosen
residential building's wall ring. Runners do not know the shelter contents at
mission start: they acquire an evacuee only through line of sight within their
20-cell search radius, then remember that contact (`e9fee785`).

The mission is an escorted relief operation, not a timed survival hold. Its
marine commander assigns every arriving squad to the shelter's exterior
approach, making the opening leg a fight through the horde to the bunker. The
barricade stays sealed until a live marine reaches within two cells of that
approach; there is no automatic fallback release (`c167e2f8`). Once relieved,
the commander retargets every live squad's `ESCORT` assignment to a moving
representative near the active cohort's center. The mission-priority escort
posture advances toward that rally cell, fires while moving, and then holds a
six-cell protective perimeter. Before relief, the lowest-id live marine squad
closes to the two-cell bunker trigger while the remaining squads use the wider
perimeter. In-flight destinations remain stable even though their cells are
claimed in the occupancy map, preventing both large-roster saturation and
per-tick path resets (`048be243`).
Civilians proceed toward the lift only while a live
marine remains within six cells of an active cohort member, and stop when their
escort falls behind (`45c6579f`). This creates the locked loop: deploy, break
through to the bunker, trigger evacuation, then fight off the swarm while
escorting the cohort to the landing zone.

The dedicated rescue factory replaces its temporary Extraction defender roster
with this payload; evacuation accounting, zero-economy terms, and moral mapping
do not change.

The payload is shipped. LOW/MEDIUM/HIGH missions install deterministic
12/24/40-runner rosters; incomplete placement discards the map attempt before
it becomes player-visible. Focused runner, pressure, roster, and factory tests
plus the full Gradle build pass. Manual playtesting remains intentionally
deferred for this session.

The first later playtest exposed one inherited behavior leak: ordinary
non-squad units roll a generic fallback response when hit, so sustained marine
fire could repeatedly stop runners before contact. `fb50b964` makes
`SWARM_PRESSURE` implacable as designed and adds full-distance melee-closure and
no-flinch regression coverage.

Debug rescue separately scales the initial swarm against simultaneous first-wave
marine seats: 2:1 at LOW, 3:1 at MEDIUM, and 4:1 at HIGH (`8b2af722`,
`e0d29078`). Counting later sortie cycles at time zero proved instantly lethal,
so they remain reinforcement depth; debug runners also begin at least 24 cells
from the shelter. This preserves a large opening swarm without changing
production event balance.

The first stat/art feel pass quarters both alien archetypes' HP: generic
`ALIEN` falls from 30 to 7.5 and `SWARM_RUNNER` from 24 to 6. This deliberately
lets marine fire delete individual bodies while roster count and implacable
movement preserve swarm pressure. Both types now carry the same presentation-only
layered animation component as marines, with continuous facing, independent head
look, and alternating feet; weapon layers are explicitly suppressed. Manual
playtesting still owns roster size and post-rebalance time-to-contact (`bccbbe16`).
The composed actor now reuses one generated forearm/claw layer twice: both arms
sit beneath the torso at rest, while contact fire alternates a forward claw
above the body and retracts it over the existing firing pose (`20d3bcb0`).

Rescue battles now sustain that pressure with mission-local roving waves
(`ad11debf`). Once live runners fall below 70% of the opening roster, a
six-second cadence restores up to 25% from deterministic reachable perimeter
cells without exceeding the opening cap. Entries avoid protected zones and keep
safe reveal distance from civilians and marines; reinforcement stops when the
cohort resolves. Civilians now evaluate a five-cell marine leash independently,
stop if they lead their nearest escort by more than two cells toward the lift,
and seek the screened side of that marine when a visible enemy is nearby.
The first follow-up playtest found that the marine six-cell hold radius could
deadlock against that two-cell civilian leash during continuous contact.
Marine squads now rally five reachable route cells ahead of the cohort while
retaining moving fire. Layered civilians turn toward new travel bearings at
180 degrees per second and retain their last idle facing, removing rapid visual
heading flips as screening paths change (`bc29cb26`).

### Outcome closure — first checkpoint

The debug mission client now exposes direct LOW/MEDIUM/HIGH **SWARM RESCUE**
entries. They launch the production civilian-rescue battle factory but use a
dedicated debug source with no campaign-event identity, so resolving one cannot
mutate a real event row. All three remain zero-payout and bring no scripted air
support.

Controlled fixtures install both the real eight-member cohort and a real swarm
roster, then verify zero, partial, and full sealed evacuation reports without
consulting battle victory. Mission outcomes retain both the representative
count and the campaign-scaled count. The debrief renders those facts explicitly
(`3 of 8 reps · 300 of 800 civilians`, for example). Distress Net now retains
the newest terminal rescue with its market, exact result, and resolution day.
That dispatch exposes no hidden moral axes and creates no economic reward.

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

The second concrete archetype is complete as **defector asylum** in
[`living-world/complete/slice-g8-defector-asylum.md`](living-world/complete/slice-g8-defector-asylum.md).
It is intentionally campaign-only: an already-discovered political chain
produces a costly asylum request and a delayed buyout, forcing the player to
keep or explicitly break a promise. This adds an honest integrity source and a
two-stage event lifecycle without cloning the rescue battle.

The third archetype is complete as **Silent Colony** in
[`living-world/complete/slice-g9-silent-colony.md`](living-world/complete/slice-g9-silent-colony.md).
It persists a blind expedition promise, selects one-shot live
decivilized/abandoned/ruins sites deterministically, and reconstructs exact-cost
choices through the registered Dead Letter. Its dedicated mission reveals a
hidden-seed automated threat, exact survivor cohort, and physical archive;
strict lineage-bound closure preserves the measured report in Dead Letter and
Chronicle exactly once across save and replay.

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
- Battle-tier: shipped as `SWARM_RUNNER` + `SWARM_PRESSURE` on the existing
  opposed `Faction.DEFENDER` battle side. This preserves two-sided battle
  assumptions while keeping the biological threat out of infantry GOAP,
  equipment, reinforcement, and ranged-fire systems.

## Related

- [moral compass](moral-compass.md) — events are how the compass gets
  tested.
- [`themes.md`](themes.md) — the Chain/Contract streams events sit beside.
- [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]] — the durable principles
  events serve.
