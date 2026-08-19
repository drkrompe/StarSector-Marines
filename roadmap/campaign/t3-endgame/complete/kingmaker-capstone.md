# T3 — the last testament

**Status:** COMPLETE (2026-08-19)

**Implemented:** `946262b2`, `15c017ed`, `379d8989`, `e0d7c117`

## Purpose

The kingmaker capstone is the moral compass's single explicit reveal. After the
player decisively helps a claimant overthrow a faction, the displaced ruler's
last testament describes the kind of commander the player's accumulated choices
have made them. It is a judgment grounded in remembered acts, not a scorecard.

This is an epilogue to an attributed political victory. It does not add another
battle, alter the throne-claim result, or reward the player for optimizing a
hidden alignment.

## Trigger — locked v1

Seal exactly one testament for a throne claim when all of these persisted facts
are true:

- the throne claim is `APPLIED`;
- its player consequence is `APPLIED`;
- the captured allegiance is `CLAIMANT`;
- captured contribution is at least 60, the existing decisive/kingmaker band;
- the source is a resolved `CIVIL_WAR` whose claimant and displaced houses match
  the claim and remain valid historical identities; and
- the moral ledger contains the source-unique `CIVIL_WAR_CLAIMANT` row for that
  chain.

Autonomous victories, incumbent defenses, minor or substantial participation,
failed/malformed claims, and unattributed outcomes do not produce this capstone.
Diplomacy writeback is deliberately not a prerequisite: a transient or rejected
external relationship mutation must not erase an otherwise established player
choice or ownership result.

The producer runs immediately after `MoralCompassSystem`. This guarantees the
claim's institutionalism choice is present before the testimony is frozen.

## Immutable testimony snapshot — locked v1

Append one source-unique `kingmakerTestaments[]` row keyed by throne-claim id.
It freezes:

- testament, throne-claim, and source-chain identity;
- claimant and displaced-house identity;
- source faction, result faction, and market identity;
- decisive player contribution;
- all four hidden moral-axis values;
- the moral-ledger row-count boundary available when the testament was sealed;
  and
- sealed day plus delivery lifecycle.

The ledger boundary is an immutable prefix. Later choices may append new ledger
rows and move the live aggregate axes, but neither can rewrite this testament.
Later editorial selection may cite only source rows inside the frozen prefix.
Legacy saves backfill an absent table with empty arrays, sentinel identities and
ticks, and a `NONE` default state; they never synthesize a testament from an
old un-attributed victory.

The snapshot stores internal values for deterministic editorial selection. No
player-facing surface may print axis names, numeric values, thresholds,
contribution totals, ledger ids, or deltas.

## Editorial voice — locked v1

The testament is written in the displaced ruler's voice. It may be hostile,
grudging, wounded, or unexpectedly respectful, but it must remain factually
bounded by the frozen source identities and ledger prefix.

The final text has three movements:

1. **Accusation:** the concrete political act—who the player helped depose whom,
   and where.
2. **Witness:** two or three remembered choices selected from distinct source
   families when available. These are described as acts and consequences, never
   as alignment labels or arithmetic.
3. **Verdict:** one concise synthesis of the player's character. Mixed axes
   should produce tension rather than forcing a saint/monster binary.

If the frozen history has too little evidence for an axis, the testament remains
silent about that dimension. It never invents off-screen conduct to fill a prose
template.

### Evidence selection — locked v1

The editor considers only ledger rows below the testament's exclusive frozen
boundary whose happened and recorded days are no later than the sealed day. A
candidate must still agree with its immutable source facts; a source id, terminal
state, outcome, or historical day mismatch discards that candidate rather than
guessing what occurred.

The three v1 witness families are prior civil wars, civilian rescue, and
defector asylum. The coronation civil-war row is excluded because the accusation
already names that act. Choose at most one deed per family. Within a family,
prefer the row with the greatest total applied moral weight, then the later
happened day, then the lower stable moral-choice id. Finally order selected deeds
by happened day and id so the testimony reads as a history. Thus row iteration
order is never a narrative tiebreaker.

Rescue prose distinguishes refusal, fewer than half saved, at least half saved,
and everyone saved without printing counts. Defector prose distinguishes refusal
before a promise, protection after a buyout, and explicit betrayal for payment.
Prior civil-war prose distinguishes overthrowing a ruler from defending one.

### Verdict synthesis — locked v1

Internal verdict clauses are eligible at an absolute axis magnitude of 10. If
the three human-conduct axes contain
both protective/principled and exploitative/expedient signals, select the
strongest clause from each side so the contradiction survives even when another
axis has a larger magnitude. Otherwise select the strongest two eligible axes.
Absolute magnitude ranks first; integrity, mercy, stewardship, then
institutionalism is the fixed tie priority.

The final sentence joins compatible clauses with “and” and opposed clauses with
“yet.” With one supported clause it states only that judgment; with none it says
the record denies the ruler a simple name. These thresholds and rankings remain
internal. The output contains deeds and character language, never axis labels,
pole labels, values, ids, dates, counts, or contribution bands.

## Delivery and persistence

The content snapshot begins `SEALED`. A later dedicated Last Testament intel
entry reconstructs from every sealed row after load and moves the independent
delivery lifecycle to `REVEALED` when the player reads it. The entry remains as
historical correspondence afterward.

### Intel delivery — locked v1

One sector-wide `Last Testament` intel plugin is registered on load and remains
hidden while no `SEALED` or `REVEALED` rows exist. It needs no duplicated plugin
payload: every opening reconstructs authenticated drafts from campaign state and
orders them newest sealed day first, then higher testament id. A valid historical
entry remains visible after reveal.

The presentation resolves houses through their stable campaign rows and markets
through the persisted registry slot plus the live economy display name. If the
testament's identities, frozen boundary, source facts, or required display names
cannot be resolved, that row produces no prose and remains sealed. Opening the
intel moves only successfully rendered `SEALED` rows to `REVEALED`; repeated
opens and save/load reconstruction are no-ops for already revealed rows.

The entry has no choice buttons, reward claim, date/count display, moral tooltip,
or consequence callback. It may point at the newest rendered testament's market
on the sector map, but it never mutates that market or any other campaign table.

### Chronicle cross-link — locked v1

Sealing a testament appends one confirmed, intimate
`KINGMAKER_TESTAMENT` Chronicle dispatch. It snapshots the testament id, source
chain, claimant, displaced ruler, source/result factions, market, and sealed day.
This is distinct from the earlier epic/intimate `THRONE_CLAIM_APPLIED` world-news
dispatch: the faction flip reports public history, while the testament records
private correspondence delivered because of the player's decisive role.

Chronicle production is testament-id unique and recovery-safe. If a legacy or
interrupted state contains a valid testament without its dispatch, the capstone
system appends the missing link on a later daily tick; it never edits the earlier
faction-flip snapshot or appends a duplicate.

### Debug reachability — locked v1

The dev-only campaign intel exposes one `Spawn kingmaker testament` control. It
deterministically selects the lowest-id valid pair of active same-market,
same-faction houses, constructs a resolved decisive claimant civil war, and then
uses the production player-consequence, moral, testament, and discovery systems.
It mirrors the local post-writeback house result but deliberately does not call
the irreversible vanilla ownership or diplomacy ports. Existing testament
history is returned rather than duplicated, and absence of a valid pair fails
without partial mutation.

Delivery is not a second consequence pass. Opening, dismissing, reconstructing,
or reloading the intel cannot change reputation, moral axes, campaign events,
contracts, houses, chains, markets, or factions.

## Slices

1. ~~**Sealed testimony authority** — append the persistent snapshot table,
   legacy backfill, source-unique producer after the moral pass, and replay/order
   tests.~~ Shipped in `946262b2`; the producer runs immediately after the moral
   pass, requires an exact handoff-day claimant ledger row, and freezes identities,
   axes, contribution, and the exclusive ledger boundary exactly once.
2. ~~**Evidence editor** — deterministic pure selection from the frozen ledger
   prefix, source-family diversity, mixed-character synthesis, and exhaustive
   prose tests without numeric leakage.~~ Shipped in `15c017e`; the pure editor
   validates terminal source facts, selects at most one strongest historical deed
   per family, preserves mixed-character tension, and rejects malformed drafts
   without exposing hidden-system vocabulary or numbers.
3. ~~**Last Testament intel** — diegetic delivery, load reconstruction,
   historical terminal state, and malformed-data fail-closed behavior.~~ Shipped
   in `379d8989`; one hidden-until-earned intel entry reconstructs authenticated
   drafts newest-first, reveals only successfully rendered rows, preserves read
   history, and performs no consequence or reward mutation.
4. ~~**Closure and reachability** — production-shaped debug setup, Chronicle
   cross-link, save/replay matrix, documentation closeout, and manual smoke.~~
   Shipped in `e0d7c117`; testament sealing now emits one recovery-safe intimate
   Chronicle link, and the dev intel can build the full capstone through the
   production consequence/moral/testament/discovery path without calling
   irreversible vanilla ports. Automated replay coverage passes; manual UI
   smoke was deliberately deferred for this session.

## Acceptance

- One qualifying claim produces one immutable content snapshot across repeated
  daily ticks and save/load.
- A snapshot includes the claimant moral row and excludes all choices appended
  after its frozen ledger boundary.
- Later moral choices cannot change its axes, evidence candidates, identities,
  or verdict.
- Only decisive, attributed claimant victories qualify.
- The player sees remembered deeds and a verdict, never a hidden-system readout.
- Missing or inconsistent source data fails closed without fabricating prose or
  replaying campaign consequences.

## Completion record

The complete capstone now runs from decisive attributed claimant victory through
an immutable moral-history snapshot, deterministic evidence editing, persistent
Last Testament delivery, and a distinct Chronicle correspondence link. Focused
tests cover malformed inputs, post-seal moral changes, missing-link recovery,
stable house-pair selection, and save-style reconstruction/replay. The full
root `:test` suite passed on 2026-08-19.

## Non-goals

- No new combat, contract, material reward, reputation change, colony reward, or
  faction mutation.
- No moral-compass dashboard, encyclopedia entry, hover tooltip, debug readout,
  or reusable alignment UI.
- No generated free-form text and no attempt to cover every possible moral
  source in the first editorial pass.
- No capstone for autonomous claimant victories or incumbent victories in v1.
