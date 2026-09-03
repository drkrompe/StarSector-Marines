# Lift and conversion

Status: IN PROGRESS

Written: 2026-09-02

The seat-sized lift (`battle.conquest.derivedLift`, off) lands the whole force
early and converts none of it. The traces say the two canonical fixtures fail
it for two different reasons, and neither of them is "more marines is worse".

## The diagnosis

**reinforced-south never got more lift at all.** `OrbitalLift.pairsFor` returns
2 pairs for that fixture's 204 seats against the ferry's `FERRY_DROP_ZONES = 3`;
the derivation only exceeds three pairs above about 397 seats. So the switch
that was supposed to add lift *removed* a third of it, and marines delivered by
tick 2926 fell from 214 to 148. `OrbitalLiftTest` never noticed: it asserts only
that the larger force gets more lift than the smaller one, never that either
gets at least the ferry.

**full-strength-west got the force and converted none of it.** 288 delivered by
2926 against 212, and 89–93% of the live force assigned to one `CLEAR_ZONE`
target in both runs. `ConquestCommand.targetChoice` bottoms out in
`nearestDefenderZoneInStrip`, which consults nothing about how many squads are
already on a zone, and `TRACK_LINE_LEAD_CELLS = 8` bounds staging, so the
surplus queues in depth behind the same target. Peak members inside any capture
zone moved 46 → 48; the front is no further forward; deaths in one 40x40 block
went 12 → 24. Total useful slots are 5–9 and fixed (`STRIP_COUNT = 3`,
`desiredSquads` 1–2 per compound, `CAPTURE_FRONT_REACH_CELLS = 24`).

**The west has a second sink of its own.** `AIRBASE@502,242`, `AIRBASE@523,193`
and the `BEACHHEAD` report `captureZoneId == exterior zone`, and phase 2's
`squadAdjacentToCompound` — which deliberately applies no track bound, because a
squad standing in the building has not been sent anywhere — returns true for any
squad standing anywhere in that exterior zone. Squads therefore hold
`SECURE_COMPOUND` on an airbase 300 cells east for the whole battle without
arriving: 16 of 45 secure-travel episodes end in squad loss and 24 of 45 never
reach the portal, against 6 of 33 on the south.

## The three changes

Each is measured separately: 1 and 2 move opposite ways on the south and would
net to nothing if measured together.

1. **Derived lift never below the ferry.** `OrbitalLift.derivedShape` returns
   `max(pairsFor(...), FERRY_DROP_ZONES)` pairs. The floor is the point: a
   derivation that can return *less* lift than the shape it replaces is not a
   sizing, it is a regression with a switch on it.
2. **Cap squads per zone target.** A nearest forward defender zone already
   carrying its own quota plus a stated overflow is skipped for the next forward
   zone in the strip; the existing choice stands when there is no other zone.
3. **Bound the adjacency claim.** Phase 2's "already there" gate takes a
   distance bound, and a compound whose capture zone is the exterior zone is
   refused as a *distant* secure target — it may still be taken by a squad
   actually inside its footprint.

## Acceptance

Measured at `-PmaxTicks=3000` per change, then the full matrix both ways.

- **1** — reinforced-south with `derivedLift=true`: delivered by ~2926 back to
  ~214 (alive + dead marines off the trace).
- **2** — full-strength-west, `derivedLift=true`: share of live force on one
  target under 50% (from ~89%), and peak members inside a capture zone above 48.
  That is the conversion reading; captures follow it.
- **3** — full-strength-west, `derivedLift=true`: secure-travel "never at
  portal" below 24/45 and squad-loss exits below 16/45, without
  `SECURE_COMPOUND` captures dropping.
- **Matrix** — chain on, `derivedLift` off and on, both fixtures, against
  baseline south 22 / 19 / 146 and west 11 / 7 / 430 (captures / held / marine
  losses). If `derivedLift` on then holds at least as much as off on both
  fixtures, it flips on by default; otherwise it stays off and the reason is
  recorded. Changes 2 and 3 ship regardless provided they do not lose held
  ground with `derivedLift` off — they are defects.

Out of scope, filed on the board instead: `STRIP_COUNT = 3` with `stripFor`
reading the landing lateral, so 100% of the west's squads prefer track 1.
