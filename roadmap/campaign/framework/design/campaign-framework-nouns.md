# Campaign framework nouns

Status: ACTIVE

Written: 2026-08-23

## Purpose

The campaign framework is the durable runtime substrate below campaign features.
It gives the tier one persisted state boundary, one monotonic measure of elapsed
campaign time, and one ordered place where autonomous behavior runs. It does
not define houses, contracts, events, personnel, or their policies; those
concepts belong to their owning campaign features.

`architecture.md` remains the canonical commitment for how campaign state is
stored and accessed. `mechanics.md` remains the canonical model of the
political simulation. This document owns the coordination contract that lets
those models stay coherent over a save's lifetime.

## Vocabulary

- **Campaign state** is the save-persisted boundary shared by campaign
  features. It survives a load; runtime behavior does not.
- **Campaign system** is a stateless unit of autonomous campaign behavior. It
  declares the state domains it reads and writes, then applies one day of its
  own policy.
- **System order** is the registered serial order in which campaign systems
  see and change state for a day. It is behaviorally significant whenever one
  policy creates, settles, or exposes work consumed by another.
- **Campaign day** is the tier's monotonic elapsed-time coordinate. It is for
  durations, deadlines, cadences, and persisted `*Tick` facts.
- **Calendar date** is Starsector's human calendar presentation. Its day
  component repeats each month and therefore is never an elapsed-time
  coordinate.
- **Clock anchor** pairs a timestamp with the campaign-day number it denotes,
  allowing the monotonic coordinate to continue from a legacy save's existing
  scale.

## Laws

1. Every campaign duration or cadence uses campaign day (or its fractional
   form when the owning domain genuinely needs sub-day precision). A calendar
   day component may format a date, but must never arm or expire campaign work.
2. Campaign day never decreases. A clock rewind or re-anchor may pause
   progress, never make elapsed-time rules fire again.
3. A new save begins at zero. An older save anchors at its last recorded
   campaign-day scale rather than jumping to an absolute epoch; this preserves
   the relative meaning of stored timers. It cannot reconstruct timers already
   corrupted before the monotonic clock shipped.
4. Persistent state is data, while tick behavior is transient and rebuilt on
   game load. A system must declare its state reads and writes even though the
   current scheduler remains serial.
5. When the framework observes a new campaign day, it runs one ordered pass
   through the registered systems. New behavior joins that seam rather than
   creating an unrelated autonomous tick.
6. Development time skipping is an explicit simulation aid: it advances the
   campaign coordinate and gives each crossed day its normal system pass.
   Re-running a pass at the current day is observation/debugging, not time
   travel.

## Ownership and flow

The mod lifecycle installs the state boundary before any consumer that needs
campaign time. The clock derives the current campaign day from the persisted
anchor. On a new day, the framework walks the registered systems in their
declared order; those systems apply the policies owned by contracts,
living-world, personnel, narrative, and the political simulation. Their
persisted outcomes then become inputs to UI and mission seams owned by those
features.

The framework owns neither a universal domain policy nor a second event bus.
A feature's mission resolution, player-facing presentation, and vanilla
writeback stay at their respective feature boundaries. In particular,
`t3-endgame` owns the exceptional vanilla-state handoff, while the framework
only provides the persisted time and ordered simulation seam it uses.

## Extension rules

Add a campaign system only when the behavior is autonomous and day-driven;
interactive actions and one-shot mission resolution should call their owning
feature policy directly. Make ordering dependencies explicit before insertion,
and update the system-order coverage with the new relationship. New persisted
campaign facts follow `architecture.md`; new political vocabulary and policy
belong in `mechanics.md` or the feature that owns it.

The remaining framework work is tracked by
`monotonic-clock-live-acceptance.md` and
`monotonic-clock-contract-comments.md`.
