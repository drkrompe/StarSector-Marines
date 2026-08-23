# Monotonic clock live acceptance

Status: READY

Written: 2026-08-23

## Outcome

Confirm in a running campaign that the elapsed-time coordinate used by campaign
features advances once per in-game day, continues across a calendar rollover,
and preserves a loaded save's timer scale.

## Scope

Use the development campaign panel and a real campaign clock. Observe the
reported campaign day before and after ordinary time passes, then cross a
calendar-month boundary. Exercise the explicit day-skip controls separately
to confirm that a skipped interval advances the coordinate and runs its normal
daily behavior. Load an existing save when available to inspect that its first
anchor continues from the stored scale rather than resetting it.

Do not change time representation, timer policy, date-display copy, or a
feature-specific deadline as part of this acceptance pass.

## Acceptance

- Ordinary time advances campaign day by elapsed days without a monthly reset.
- A duration armed before a calendar rollover remains due after the rollover,
  not before or indefinitely after it.
- Skip 1, 7, and 30 days advance campaign day by their requested amounts and
  cause the same daily behavior as the corresponding interval.
- Re-running the current daily pass does not itself advance campaign day.
- A loaded legacy save preserves its prior day scale at first anchor; a new
  campaign begins at zero.

## Context

This validates the standing contract in `campaign-framework-nouns.md`; the
headless arithmetic coverage is already in code.
