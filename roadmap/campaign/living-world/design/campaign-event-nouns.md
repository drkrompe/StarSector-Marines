# Campaign event nouns

Status: SHIPPED

Written: 2026-08-23

## Purpose

A **campaign event** is a sparse, attributable interruption to the campaign:
it offers a concrete choice, freezes the facts required to honour that choice,
and accepts one explicit terminal report. It is neither a generic quest engine
nor a second contract system.

## Lifecycle

An event begins from a deterministic source and freezes its source identity,
terms, deadline, and any hidden authority needed after commitment. While an
event is open, no competing event source may create another open choice.

The player may refuse, let an initial choice expire, or commit its required
resources. Commitment is atomic and does not fabricate an outcome. A committed
event stays open until its own authorised report resolves it. Repeated input,
save/load, and later source changes cannot charge, pay, or settle the same
event twice. Terminal reports are measured facts, not an inference from a
generic battle victory.

## Event families

**Civilian rescue** is a time-bounded relief commitment at an eligible market.
Its dedicated operation reports the evacuated portion of a frozen civilian
cohort. Refusal and expiry are distinct from a measured rescue result.

**Defector asylum** begins from a sufficiently established discovered political
chain. The player may pay for asylum, then later protect the promise or betray
it for the frozen offer. Silence at the later decision protects the defector;
it is not invented betrayal.

**Silent Colony** is a blind expedition to one deterministic dead site. The
player sees only the commitment terms before deployment. Its dedicated
operation independently reports survivor and archive facts; it does not imply
loot, moral intent, recolonisation, or a generic salvage reward.

## Moral meaning

The **moral compass** is a hidden, bounded record of explicit, attributable
choices. Each source may write at most once. It may record the meaning of a
refusal, promise, rescue, or credited political intervention where that meaning
is explicit; it must not infer motive from casualties, archive loss, expiry,
or a raw battle result. Numeric axes are not a player-facing event surface.

## Boundaries

`living-world-nouns.md` owns the political field and whether a discovered
chain can source a political event. `contracts-nouns.md` remains the authority
for paid political service. The battle layer owns operation setup, tactical
pressure, and objective execution. `narrative-nouns.md` owns prose, while an
event owns the frozen facts that prose may safely state.

## Extension laws

- Add an event family only when it has a concrete source, frozen commitment,
  explicit terminal authority, and a reason not to be a contract.
- Reuse the shared open-event gate and exactly-once lifecycle; do not create a
  parallel event scheduler or presentation-owned mutation path.
- Do not reveal hidden stakes, threats, rewards, or moral meaning before the
  event's commitment and report boundaries allow them.
