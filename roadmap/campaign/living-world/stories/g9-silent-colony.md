# G9 — Silent Colony

**Status:** SLICE 3 CODE COMPLETE (2026-08-19)

**Implemented:** `4d50805d`, `33b073bd`, `9a87c85b`

## Purpose

The third black-swan archetype begins with an automated distress burst from a
colony the Sector already considers dead. The signal cannot establish whether
anyone remains alive or what ended the settlement. The player may spend real
supplies and fuel to mount a blind expedition, or leave the site alone.

This is exploration under moral uncertainty, not another patron contract and
not civilian rescue in different scenery. Committing buys only the chance to
learn the truth. A later dedicated mission reveals stranded survivors, a sealed
colony archive, and an automated threat only after the company is already on
site.

## Source and cadence — locked v1

- A source is a live market identity with a primary entity and valid mission
  target. A planet-condition-only site qualifies through scattered, widespread,
  extensive, or vast ruins; any site qualifies through `decivilized` or
  `abandoned_station`. Ruins on a functioning colony do not qualify by
  themselves.
- The producer selects directly from live site identities, then interns only the
  chosen market. Hidden/uncolonized ruins therefore remain reachable without
  pre-populating campaign state with every surveyed world. Later recovery,
  recolonization, or condition changes cannot rewrite the frozen event.
- A site may source at most one Silent Colony event. The trigger key is derived
  from stable site identity, never array order or the current day.
- Starting on day 90, a deterministic 90-day epoch may choose at most one
  eligible site. The common one-open-event gate prevents overlap with civilian
  rescue and defector asylum.
- V1 is one-shot per site. Terminal history remains visible but never makes the
  same dead colony call again.

Ruins severity becomes tier 1–4 from scattered through vast. Decivilized and
abandoned-station sources use at least tier 2. Frozen terms are `20 + 10 × tier`
supplies, `10 + 5 × tier` fuel, and `4 + 2 × tier` representative survivors.
The threat seed is a stable site-key derivation; neither candidate enumeration
order nor market-registry insertion order affects selection or terms.

## Frozen expedition facts — locked v1

Preparation freezes:

- event, trigger, and market identity;
- creation day and a three-day initial choice deadline;
- exact supplies and fuel required for the expedition;
- a positive representative survivor count used by the later battle bridge;
- a non-negative hidden threat seed; and
- archive outcome, initially `NONE`.

The hidden seed is simulation authority, not player-facing prose. Repeated
preparation for the same trigger returns the original row without changing its
cost, stakes, seed, or dates.

## Lifecycle — locked v1

1. `PENDING_CHOICE`: **Mount the expedition** atomically consumes the frozen
   supplies and fuel and enters `COMMITTED`. **Leave it silent** enters terminal
   `REFUSED`. Passive deadline expiry enters `EXPIRED`; neither refusal nor
   expiry fabricates knowledge about the colony.
2. `COMMITTED`: the event remains open until its lineage-bound mission reports
   an outcome. Commitment alone never invents survivors, an archive, a threat,
   loot, or moral meaning.
3. `RESOLVED`: the battle bridge atomically records a survivor count clamped to
   the frozen representative cohort, explicit archive `LOST`/`RECOVERED`, and
   resolution day. Zero survivors plus a lost archive is a valid measured
   failure, distinct from an unresolved row.

Repeated choices, daily ticks, reconstructed intel, mission replay, and
save/load cannot consume resources or resolve the expedition twice.

## Mission promise — locked v1

The eventual mission is a dedicated campaign-event expedition, not a generic
contract or salvage screen. The company enters a ruined settlement with neither
enemy roster nor reward preview. Contact reveals two independent truths:

- a small group of stranded survivors who must reach extraction; and
- a sealed archive that must be physically recovered to establish what happened.

The hidden seed deterministically selects the automated threat profile and
placement. V1 does not reuse the biological swarm: the reveal should feel like
the colony's own dormant systems waking up. Survivors and archive may both be
recovered, one may be lost, or both may be lost. Battle victory alone is not an
outcome report.

## Consequence and presentation direction

- A global **Dead Letter** intel entry reconstructs the pending signal,
  committed expedition, and terminal report from `CampaignState`.
- Before commitment it shows site identity, exact cost, and deadline, but not
  survivor count, archive contents, threat type, moral effects, or reward.
- Terminal copy states only measured survivor/archive facts. It never claims
  why an objective was lost.
- Later moral recording may reward an explicit player priority or promise, but
  must not infer intent from casualties or archive loss. Slice 1 therefore
  records no moral row.
- Archive recovery is narrative evidence in v1. Any material tech reward needs
  its own later contract and must use an exactly-once settlement seam rather
  than mutating cargo from presentation code.

## Persistence — locked v1

Append `SILENT_COLONY` to `CampaignEventType`; existing ordinals never move.
Reuse the common event market, cost, choice, civilian-stakes, and resolution
columns. Append:

- `eventColonyThreatSeed`, backfilled and grown with `-1`; and
- `eventColonyArchiveOutcome`, using append-only `NONE`, `LOST`, `RECOVERED`.

Legacy saves receive empty/sentinel columns and never synthesize an expedition.
A resolved Silent Colony row with archive `NONE`, negative survivor facts, an
invalid market, or an invalid seed fails closed in later presentation and
consequence code.

## Slices

1. ~~**Persistent expedition authority** — append type-specific storage and
   legacy backfill; implement pure prepare/commit/refuse/resolve transitions;
   lock resource atomicity, idempotency, and replay behavior in tests.~~ Shipped
   in `4d50805d`; the new event type, hidden threat seed, explicit archive
   outcome, atomic funding policy, neutral refusal/expiry, clamped mission
   report, growth sentinels, and legacy backfill are regression-covered.
2. ~~**Ruins-site producer and Dead Letter** — deterministic source selection,
   common open-event gate, frozen terms, load reconstruction, and initial choice
   surface.~~ Shipped in `33b073bd`; one unused dead site is selected per
   90-day epoch without registry-order coupling, only the chosen identity is
   interned, and the registered hidden-until-authenticated Dead Letter routes
   funding/refusal through the Slice 1 authority while withholding survivor,
   archive, threat, reward, and moral facts.
3. ~~**Expedition mission lineage** — event mission key/factory, deterministic
   automated threat reveal, survivor cohort, physical archive objective, and
   explicit dual outcome report.~~ Shipped in `9a87c85b`; committed rows emit a
   stable zero-economy local mission carrying the frozen threat seed and exact
   6–12-member cohort. The hidden seed freezes one of three autonomous
   turret/drone defense profiles and its placement without biological swarm or
   conventional infantry. A separate five-second archive-room recovery and
   terminal survivor accounting produce independent debrief facts; unfinished
   battles retain archive `NONE` rather than inferring loss from victory.
4. **Closure and reachability** — strict outcome bridge, terminal Dead Letter,
   Chronicle report, debug setup, full save/replay matrix, and documented moral
   decision only if the mission adds an explicit player priority.

## Acceptance

- Trigger replay preserves the first frozen snapshot and creates one row.
- Invalid sites, costs, dates, survivor facts, or threat seeds append nothing.
- Refusal and expiry charge nothing and reveal nothing.
- Commitment is atomic and charges once; it cannot resolve without a later
  explicit report.
- Resolution clamps survivors, requires explicit archive loss/recovery, and is
  immutable after the first terminal write.
- Existing rescue and defector event ordinals, columns, and behavior remain
  compatible.

## Non-goals

- No patron, contract payout, generic salvage entitlement, immediate tech drop,
  colony ownership change, or market resurrection.
- No free-form procedural text, general event DSL, generic multi-objective
  framework, or biological-swarm reuse.
- No moral inference from battle success, survivor deaths, or archive loss.
- No mission, intel UI, producer, automated enemy, Chronicle dispatch, or cargo
  reward in Slice 1.

## Slice verification

Focused Silent Colony, shared event-column, civilian-rescue, defector-asylum,
lifecycle, producer-order, and Dead Letter reconstruction tests pass. The
Slice 3 factory additionally locks maximum cohort placement, autonomous-only
defenders, battle-seed-independent threat placement, and independent survivor /
archive reports. The complete root `:test` suite also passes on 2026-08-19.
Manual Dead Letter, mission, battle, and debrief UI validation remains deferred
with the shared campaign verification queue.
