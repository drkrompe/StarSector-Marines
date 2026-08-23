# Rescue pressure acceptance

Status: PARKED

Written: 2026-08-23

## Goal

Validate the current civilian-rescue pressure envelope in a real battle without
silently restoring the superseded legacy health values.

## Current envelope to observe

The production rescue opens with 64 runners; the retained LOW/MEDIUM/HIGH
stress envelopes open with 20/40/64. Current code gives the generic alien 15 HP
and the runner 20 HP, with expected service-fire breakpoints of three pulse
hits, four SMG hits, and two DMR hits against a runner. These are observations
for this acceptance pass, not a claim that they are permanent balance law.

## Checks

- Verify time-to-contact, kill cadence, runner replenishment, and whether
  marine screening remains a meaningful protective choice.
- Verify squad spacing, pickup approach, claw readability, and evacuation
  pressure at normal play zoom.
- Exercise both the production-shaped rescue and the stress scenarios; preserve
  the 20/40/64 roster distinction in any follow-up.

## Outcome

Record observed balance evidence. Create a separate balance-policy story only
if that evidence identifies a concrete change; this acceptance pass does not
pre-authorize numerical retuning.
