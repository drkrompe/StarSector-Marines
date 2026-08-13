# Captain moral trait drift

**Status:** SLICE 1 COMPLETE — deterministic drift system next (2026-08-12)

**Implemented:** `b9e8ffd6`

## Purpose

The hidden moral compass needs its first restrained world reaction. Long-serving
captains may become known as `IDEALIST` or `CYNICAL` after witnessing a sustained
pattern in how the company treats people and obligations. The reaction belongs
to the captain record and roster presentation; the player never sees moral-axis
names, scores, thresholds, or a `+trait` optimization notification.

This is an outlook earned from company history, not an assertion that the
captain personally approved every choice. Later stories may use that outlook for
comments, disagreement, or transfers. V1 grants the durable characterization
only and does not attach a combat modifier.

## Eligibility

- A captain must have served for at least **90 campaign days**. Service age is
  `currentDay - createdAtDay`; ACTIVE, INJURED, and GARRISONED time all counts.
  KIA captains never drift.
- At least **three recorded moral choices** must have happened on or after the
  captain joined. Pre-recruitment company history cannot characterize a new
  hire.
- Only the ledger's applied mercy, integrity, and stewardship deltas count.
  Institutionalism is deliberately excluded: supporting a claimant or an
  incumbent establishes political alignment, not idealism or cynicism.
- The captain must not already have resolved a moral outlook.

## Classification

For eligible post-recruitment ledger rows, sum the exact applied deltas on each
of mercy, integrity, and stewardship. Clamping is already represented in the
ledger, so the drift system never reconstructs intended/unapplied magnitude.

`IDEALIST` requires all of:

- combined mercy + integrity + stewardship is at least **+45**;
- at least two of the three axis sums are at least **+10**;
- none of the three is below **-15**.

`CYNICAL` is the exact inverse:

- the combined sum is at most **-45**;
- at least two axis sums are at most **-10**;
- none is above **+15**.

Everything else is unresolved. Mixed evidence remains mixed; the system does
not choose whichever side happens to be numerically closer. These gates make a
single spectacular rescue insufficient by itself and prevent political choices,
which currently affect only institutionalism, from granting either outlook.

## Persistence and replay

- Append `IDEALIST` and `CYNICAL` to `Trait`; never reorder existing enum values.
- `MarineCaptain` persists `moralOutlookTrait` plus `moralOutlookDay` (`-1` when
  unresolved). The dedicated fields are the exactly-once authority; the general
  trait list is presentation/mechanical consumption and is repaired from them.
- On the first daily tick satisfying a classification, atomically set the
  outlook fields, add the matching trait if absent, and append one diegetic
  commendation. Repeated ticks and save/load cannot append again.
- An outlook never reverses and a captain never owns both outlook traits in V1.
  It records who they became during this period of service, rather than tracking
  the company's current aggregate direction.
- Legacy saves backfill unresolved outlook fields. If an early/dev save already
  contains exactly one outlook trait but lacks the fields, repair adopts that
  trait without adding a new commendation. Contradictory dual traits fail closed:
  remove neither, award neither, and leave the outlook unresolved for explicit
  future migration.

## Presentation

- The ordinary captain trait surface may show `Idealist` or `Cynical`, just as it
  shows existing professional traits. No tooltip or copy mentions hidden axes.
- The one-time commendation is observational: the captain "became known for an
  idealistic belief in the company's purpose" or "for a cynical view of the
  company's work." It reports character, not the triggering arithmetic.
- No popup, floating notification, moral dashboard, debug score, or choice-time
  feedback ships in this story.

## Slices

1. ~~**Persistent outlook authority** — append traits; persist/backfill the
   captain outlook fields; lock mutual exclusion and repair behavior.~~ Shipped
   in `b9e8ffd6` with focused exactly-once and legacy-repair coverage.
2. **Deterministic drift system** — captain-local ledger aggregation, eligibility,
   classification, daily-system ordering, exactly-once commendation, tests.
3. **Roster presentation and closure** — readable trait labels on the existing
   captain surface, save/replay matrices, roadmap handoff.

## Acceptance

- No captain drifts before 90 days or three witnessed choices.
- Pre-recruitment choices and institutionalism-only rows cannot cause drift.
- Strong coherent positive/negative patterns grant the correct single trait;
  mixed or contradictory evidence grants neither.
- Reload/repeated ticks never duplicate a trait or commendation.
- Existing captain saves remain unresolved and valid; existing single outlook
  traits are adopted; contradictory dual traits are not guessed away.
- No player-facing surface exposes a moral score, axis name, threshold, or
  numerical consequence.

## Non-goals

- No combat modifier for either outlook.
- No outlook reversal, captain transfer, resignation, or interpersonal conflict.
- No soldier comments, patron dialog gates, briefing voice shifts, or capstone
  speech; those remain later diegetic consumers.
