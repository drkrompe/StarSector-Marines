# Campaign narrative — the patron tapestry

> Extracted from the contract layer ([`../contracts/overview.md`](../contracts/overview.md)),
> which defines the commercial mechanics. This doc owns the *narrative*
> side: how patron stories are told, the archetype content axis, and the
> discipline for keeping procedurally-generated briefings from feeling
> tropey. Continues from [`../themes.md`](../themes.md) /
> [`../mechanics.md`](../mechanics.md).

Every contract — even the early-game desperate Tier-1 — carries
narrative weight. The narrative belongs to the *patron*, not the
player, and is delivered through interfaces the player already uses
(briefings, after-action reports, intel feed, cross-patron
references). The player is never shown a cinematic of the patron's
office; they're shown *the brief* the patron sent, and the
patron's situation leaks through its form, urgency, and gaps.

See [[feedback-world-reactive-over-expressive]] and
[[feedback-patron-narrative-discoverable]] memories for the durable
principle. Implications for what we *write*:

## The comms officer is the narrator

The player never reads a patron directly. The mission-select / briefing
surface is framed as the player consulting with their company's
**contract comms officer** — Castle from MechWarrior Mercenaries as the
spiritual reference. The officer met with the patron's representative
and is now reporting back at the strategic console. Everything the
player learns about a patron flows through this filter.

This is load-bearing: it makes patron-narrative-discoverable
architectural, not just stylistic. There's no omniscient POV anywhere
in the mission-select surface. See
[[project-comms-officer-narrator]] memory for the full design and its
implications for layout, mood lines, "ask the officer" levers, and
diegetic offer filtering.

## Composable voice layering

The briefing the player reads is composed from independent axes — no
cartesian-product content cost. Adding a new mood or a new archetype
touches one bank only.

1. **Patron archetype** (body) drives the *register* of what the
   officer reports about the patron. Lives in
   `mod/data/marines/patron_briefings.json`. Archetype-pure: no
   officer or mood references in the text.
2. **Officer mood** (prefix + optional suffix) frames the body with
   the officer's read on the company's current situation. Four buckets
   — `DESPERATE / GREEN / STEADY / SEASONED`. Lives in
   `mod/data/marines/comms_officer_voice.json`. Mood-pure: no patron
   or archetype references in the text.
2b. **Officer summary** (mission-select header line) is a sibling
   surface in the same JSON file — nested `summary.{overview, client}`
   pools per mood. `overview` is the no-client-selected header, `client`
   is the client-selected header. Rendered by `CommsOfficerSummary`;
   stable per-day so the line doesn't jitter as the player interacts
   with the screen. Tokens: `{patron}` (client form only),
   `{offerCount}`, `{clientCount}`, `{lapsingCount}`.
3. **Officer characterization** (future) — different officers will
   ship different prefix/suffix/summary pools and aside vocabularies.
   Same axis as swapping a captain. Not modeled until there's a second
   officer to compare against; the mood axis stands in with a single
   fixed characterization.

Composed by `BriefingComposer.compose(archetype, mood, contractId, ...)`
into `prefix + body + (optional suffix)`. All picks deterministic from
the contract id so save/load and re-renders produce the same text. Mood
is read from `OfficerMoodReader.currentMood()`, which derives the bucket
from vanilla `SharedData.getData().getPreviousReport()` (totalIncome /
totalUpkeep / debt / previousDebt), `Global.getSector().getPlayerFleet()`
(credits + ship count), `MarineRosterScript.roster().activeCount()`, and
`CampaignState.playerMrbRep`. The runway bands (DESPERATE below 6 months
upkeep, SEASONED requires 12+ months) anchor to the economy-doc Survival
→ Accumulation → Scale arc; see [[feedback-paycheck-runway-window]] for
the tuning intent. `OfficerHeaderWidget` caches the result per sector-day
so per-frame credit movement doesn't make the mood flicker.

## Briefing register reveals patron situation

A desperate Tier-1 non-MRB Capo's brief reads different from a polished
Tier-3 Corporate VP's — but in both cases what the player sees is the
*officer's report* on the meeting, not the patron's words. The archetype
manifests through what the officer observed:

- **Tier-1 desperate** — officer notes the patron's rep was rushed, didn't
  sit down, wouldn't explain the timeline. Reveals: no time, no resources,
  no formal process.
- **Tier-2 transactional** — officer notes a businesslike meeting, scope
  spelled out, contract terms attached. Reveals: this patron has a process.
- **Tier-3 oblique** — officer notes formality, vagueness on specifics, the
  patron implying rather than stating. Reveals: this patron is careful and
  has reputation to protect.

The officer's tone is consistent (pragmatic, observant, dry); what *shifts*
is what they noticed about the patron.

## Per-patron consistency across contracts

Same patron across multiple contracts → the officer's reports converge.
"Cavor's people again. Apologetic about the timeline this time — that's
new." The player notices the pattern through the officer's continuity.
Implementation: per-patron-archetype flavor pool indexed by `houseId`,
drawing from category-tagged strings so the officer's recollections of
the same Capo share idioms and call back to prior meetings.

## After-action reports as observation

Captain returns from a Garrison contract → their `commendations`
entry isn't just "Day 247: returned to active duty." It includes
observed-at-location details: "Noticed unusual security activity
around the patron's compound the week before the Sorenson incident."
The player's *captain* is the camera; what the captain saw, the
player learns. Never cuts away to omniscient POV.

## Cross-patron references build the tapestry

Patron B's contract briefing mentions Patron A's recent operation in
passing — not naming the player, just acknowledging that something
happened in the world. The player connects the dots. Over months of
play, this builds the sense of a universe larger than the player's
own activity.

## Discoverable, not announced

Patron arcs are *available* to the player who's paying attention, not
*shown* to the player who isn't. No "+3 to patron narrative
discovery" notification. The chain system is the mechanical scaffold;
the narrative texture is what makes a chain feel like an unfolding
patron story rather than "Mission 3 of 5."

## Early-game desperate contracts ARE narrative content

Especially early-game. The desperate non-MRB contract isn't a
mechanical stepping stone with thin flavor — it's the player's first
window into one specific patron's world. Done right, it hooks the
player to that patron's arc and seeds the discoverable narrative.

## Patron archetypes

A third content axis orthogonal to flavor (Corporate / Feudal /
Underworld / Sectarian) and rank (Tier 1-4). Each patron has an
archetype that determines briefing register, payment quirks, contract
preferences, and the *narrative hook* they offer the player. Same
archetype renders differently per flavor: a Feudal fallen-noble is a
deposed bloodline, an Underworld fallen-noble is a demoted Boss
trying to claw back territory, a Corporate fallen-noble is a fired
exec funding their comeback from severance.

Persisted as `byte archetype` on the `houses[]` row — see
[`../mechanics.md`](../mechanics.md#houses). Stable for the patron's
lifetime *unless* a state-change event shifts it (see
[procedural fatigue](#procedural-fatigue) below).

### Starter set (6 archetypes)

All briefing-register columns describe **what the officer observed about
the patron during the meeting** — never the patron's direct voice.

| Archetype | What the officer observed | Payment | Narrative hook |
| --- | --- | --- | --- |
| **TIME_RUSHED** | Rep didn't sit down, sweated through it, wouldn't say why the window was so tight | Normal, but volatile — they cut corners | "What pressure are they under?" — desperation is the texture |
| **FALLEN_NOBLE** | Envoy in person; talked about how the patron's family used to handle these in-house; trailed off | Bad / delayed | "Are they a legitimate deposed heir or a con artist?" — moral hook, refuse-for-ethics surface |
| **TRUE_BELIEVER** | Twenty minutes on what the target's doing wrong before the numbers came up; thanked us before we'd agreed | Mediocre | "Are their targets righteous, or just convenient?" — alignment vs profit |
| **ESTABLISHED** | Senior counsel, working lunch, polite, did not elaborate; "we did not press" | Premium, reliable | "Why are they using mercs instead of formal channels?" — what's embarrassing |
| **SUSPICIOUS** | Walk-in, no real name, no paperwork beyond the contract itself, met off-site | Premium, no questions asked | "What rep cost am I paying for this cash?" — MRB-rep penalty risk |
| **NEWCOMER** | Brought a lawyer who'd never bought a marine op before; binder, references requested, "service deliverables" | Overpays | "What happens when their superiors find out?" — high-variance outcomes |

These six are the starter set; the enum is open-ended so future
archetypes can be added without migration (existing rows stay valid;
new content uses new slots).

### The fallen-noble's moral hook

Worth calling out because it adds a gameplay dimension the others
don't: **refusal-for-ethics**. The contract is mechanically bad
(bad pay, dubious loot) and the player *can* walk away — but the
hint at a good cause makes refusal *cost something narratively*.
And the "good cause" may be real (legitimate DEPOSED-status patron,
ELEVATE_HEIR chain in waiting per
[`../mechanics.md`](../mechanics.md#hidden-heirs-and-displaced-claims)) or
fake (con artist trading on sympathy). The player doesn't know
upfront. Participating reveals it over months.

### Cross-archetype contrast

Most of the variety the player feels session-to-session comes from
working with multiple patron archetypes in parallel — three active
contracts with a time-rushed Capo, a fallen-noble Baron, and a
suspicious Fence reads as varied content even if each individual
patron's voice is internally consistent.

## Procedural fatigue

Codified archetypes will feel tropey / generated after N missions.
The discipline to combat this is its own perpetual content-authoring
problem; the directions below are *future work*, flagged here so the
content pipeline can be designed in dialogue with them.

### Mitigation directions

1. **Multiple register variants per cell.** Each (archetype, rank,
   flavor) cell gets 5-10 briefing templates, not one. Cheap to
   author, eliminates verbatim repeats.
2. **Modifier traits on top of archetype.** Personality dimensions
   layered on the base archetype — `FALLEN_NOBLE + SPITEFUL`,
   `FALLEN_NOBLE + RESIGNED`, `FALLEN_NOBLE + DELUSIONAL`.
   Combinatorial variety from a small base. Encoded as a second byte
   slot on `houses[]`.
3. **State-driven evolution.** Archetype is a *starting* state, not
   a permanent label. A fallen-noble who succeeds in their kingmaker
   arc transitions to `ESTABLISHED` or a new `VINDICATED` archetype.
   A time-rushed Capo who promotes shifts to established underworld
   Boss. The same `houseId` reads differently over months.
4. **History-aware briefings.** Late-game contracts from a patron
   reference *your* shared past with them, not just archetype-generic
   flavor. The voice stays consistent; the content draws from real
   campaign events (target name-checks, prior mission outcomes).
   Implementation: per-house "history of player engagement" feed
   strings sourced from MissionResolver writebacks.
5. **Rare anomaly events.** Periodic departures from pattern —
   fallen-noble has a moment of triumph, time-rushed Capo writes one
   weirdly composed brief. Signals "this is a person, not a slot
   machine." Frequency: rare (1 in 30+ briefs).
6. **Authored content for chain-arc patrons.** Procedural for most;
   hand-written briefing arcs for patrons the player is deeply
   engaged with (active chains). The chain system is the trigger —
   when a chain spans 5+ contracts from one patron, that patron
   transitions from procedural to hand-authored.

### Discipline

Tropification is not a one-shot solve. It's an ongoing
content-authoring concern that every future session adding briefing
text, archetype, or chain content should be in dialogue with. If a
briefing template can be reused verbatim across 5 patrons, the
authoring missed an opportunity — find the per-(archetype, flavor,
rank) hook that makes it specific.

## Related

- [`../contracts/overview.md`](../contracts/overview.md) — the commercial
  layer this narrative sits on top of; archetype byte lives on the
  contract's patron house.
- [`../mechanics.md`](../mechanics.md) — `houses[]` archetype column,
  hidden-heir / displaced-claim layer the fallen-noble hook draws on.
- [`../themes.md`](../themes.md) — the four house flavors archetypes
  render through.
- Memory: [[project-comms-officer-narrator]],
  [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]],
  [[feedback-paycheck-runway-window]], [[project-moral-compass]],
  [[project-black-swan-events]].
