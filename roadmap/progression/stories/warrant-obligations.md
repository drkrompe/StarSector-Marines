# Military warrant obligations

> A military warrant is Capability bought with Standing, and it has to keep
> costing something or it is just a bigger unlock.

Status: PLANNED — builds on the shipped Restricted tier (`progression-nouns.md`).
`hegemony-auxiliary-warrant.md` uses its "honoured period" for its second stage.
Written: 2026-10-15

Read `meta-progression.md` (the growth rule and the four currencies),
`progression-nouns.md` (Kit and armory) and `campaign-kit-arc.md` before
implementing.

## Problem

`meta-progression.md` refuses a feature that produces Capability with no
counter-pressure. A military warrant opens the strongest infantry equipment in
the game. If holding one asks nothing of the company, the warrant is the last
unlock rather than a relationship. And the thing the story is about, a
mercenary company becoming somebody's auxiliary, never shows up in play.

## Scope

**Service terms.** Every military warrant states its terms when granted.
Standing warrants and authored ones state them the same way:

- **Service:** in each obligation period the company completes at least one
  contract for the issuing faction, or answers one *service call*. A service
  call is an ordinary contract the issuer sends directly, with a deadline.
- **Loyalty:** the company takes no contract against the issuer, and does not
  let its relationship with the issuer fall below the warrant's floor. For the
  Hegemony auxiliary warrant the floor is Favorable. For a standing warrant it
  is the rule's own threshold.

A period in which service was done and loyalty kept is **honoured**.
`hegemony-auxiliary-warrant.md` reads that event for its second stage.

**Suspension, the counter-pressure.** A missed period, or a broken loyalty
term, suspends the warrant. While a warrant is suspended:

- its Restricted templates cannot be acquired again;
- no marine can be **newly fitted** with one of its suits. Marines already
  wearing one keep it. A casualty's suit is not reissued, and a recruit
  cannot be put into one.
- the company record shows the suspension and what restores it.

Restoration is one honoured period: a completed service call, with the
relationship back at the floor.

Nothing learned is revoked and no fitted suit is taken away. The warrant
squeezes *replacement*, which is where a powered section actually feels
pressure over a long campaign.

**Revocation.** If the relationship with the issuer falls to hostile, the
warrant is revoked rather than suspended. The suspension rules apply
permanently until a new warrant is granted. An authored warrant is not
re-offered; the standing rule may grant a fresh one later.

**Grandfathered suits are outside this.** A save that learned a Restricted
suit before the Restricted tier shipped holds it without a
warrant. Those suits can be fitted freely and carry no obligations. That is
the compatibility promise, not a loophole to close later.

## Out of scope

- Which contracts a service call offers. It uses the ordinary contract
  generator, filtered to the issuer; authored service missions are later arc
  work.
- Hegemony scrutiny beyond these terms, for example inspections or
  restrictions on other traditions' kit. Recorded in `campaign-kit-arc.md`
  as a possible later layer.

## Decisions to settle while implementing

- **Period length.** Proposed: 90 campaign days. Too short makes the warrant a
  chore that dictates the campaign; too long makes the obligation a formality.
- **Does the fitting gate need a new hook?** Learning currently permits free
  fitting wherever a sheet asks for the equipment (`a-small-war.md`). A
  suspended warrant needs fitting to ask one more question. Find the single
  place where fitting resolves a sheet's billets and put the check there, not
  in the screens.

## Acceptance

- A unit test on the period clock: a period with a completed issuer contract
  or a service call, and the relationship at the floor, is honoured. A period
  without one suspends the warrant. One honoured period restores it.
- A unit test on the fitting gate: a suspended warrant refuses a new fitting
  of its suit and leaves already-fitted marines untouched. A grandfathered
  suit is never refused.
- A unit test on revocation at hostile standing.
- Company record snapshot at 1920x1080, 100% UI scale, showing an active
  warrant with its next deadline, and a suspended one with what restores it.

## Plan

1. Terms and the period clock on the warrant record.
2. Service calls from the ordinary contract generator, filtered to the issuer.
3. Suspension and the fitting gate; revocation at hostile standing.
4. Company record legibility, snapshot, then fold into `progression-nouns.md`
   beside the military warrant.
