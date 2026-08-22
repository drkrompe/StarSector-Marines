# S4 — Performance-derived experience

> XP should be something a marine earned, and the ladder it climbs should
> be worth climbing.

**Status:** not started. Depends on
[S3](s3-per-soldier-telemetry.md) for the input data, and should land after
[S1](s1-lethality-and-tier-spread.md) so the experience ladder is retuned
against the corrected grade spread rather than against the current one.

## Problem

Two separate thinness problems that share a fix:

1. **Award is flat.** `MissionResolver.applyPersonnelOutcome` pays every
   survivor the same 30 / 50 / 80 by risk. Participation, not performance.
2. **The ladder is nearly flat too.** `ExperienceTier` spans accuracy
   x0.92 to x1.13 across four tiers — about 1.23x end to end. After S1
   widens grade to roughly a 3x effective-DPS span, experience becomes
   statistical noise next to equipment. A marine who has survived twenty
   missions should not be a rounding error beside a marine who was handed a
   better rifle.

## Goal

Make XP reflect what happened, and make the tier a marine reaches matter
enough to notice.

## Slice 1 — Earned award

Replace the flat payout with a composed award, using S3 telemetry:

- **Participation base** — scaled by risk, paid to everyone who deployed
  and came back. The floor that keeps support marines progressing.
- **Performance component** — from landed rounds, damage dealt, and kills.
- **Mission component** — victory, objectives taken, survival.

Guardrails, in order of importance:

- **Diminishing returns on the performance component.** Without it, one
  designated marksman farms the whole ladder while their fireteam stagnates,
  and the player's optimal play becomes hoarding kills for one soldier. A
  concave curve keeps a great mission meaningfully better than an average
  one without being ten times better.
- **WIA marines earn.** They were there and they paid for it. KIA marines
  do not — there is no one to pay.
- **Award must be deterministic for a frozen `MissionOutcome`**, matching
  the existing casualty-disposition invariant.
- **Losses still teach.** The current defeat payout of 10 should survive in
  some form; a hard fight lost is real experience, and this is consistent
  with [[feedback_hard_failure_preference]] — the loss should hurt in
  materiel and personnel, not in learning.

## Slice 2 — Ladder retune

Widen `ExperienceTier` so veterancy is a felt axis, and re-derive the XP
thresholds against Slice 1's new award rates (the current 100/350/800
thresholds were set against a flat 30-80 payout and will not survive the
change).

Directional proposal — tune with S1's TTK harness, do not treat as final:

| Tier | accuracy | cooldown | spread |
| --- | --- | --- | --- |
| Green | x0.82 | x1.15 | x1.20 |
| Regular | x1.00 | x1.00 | x1.00 |
| Veteran | x1.12 | x0.92 | x0.86 |
| Elite | x1.25 | x0.84 | x0.72 |

Consider a fifth tier above Elite as a long-campaign chase, reachable only
by a marine who has genuinely survived a long time. Open question below.

**Balance guard:** experience applies to defenders and employer soldiers
too. A wider ladder means veteran defenders are meaningfully harder.
Re-verify risk-scaled defender rosters, as in S1.

## Decision: no live in-battle XP for v1

`CombatService.addExperience(long, int)` exists, refreshes derived stats
immediately, and has no production caller.

**Recommendation: leave it unwired and keep XP a post-mission settlement.**
Stats that drift mid-firefight make the sim harder to reason about, make
the TTK harness measure a moving target, and give the player no readable
feedback for the change. If it stays unwired after this story, either
delete it or document it as a deliberate seam — an unused public method
with no explanation is the kind of thing that reads as an oversight later.

Counter-case worth hearing: a visible "promoted mid-battle" moment is a
strong beat in this genre. If that is wanted, it belongs in
[S9](s9-in-battle-quality-conveyance.md) as a *presentation* of a
threshold crossed, with the stat change still settled afterwards.

## Out of scope

- Captain XP and the `Rank` ladder. Captain progression is a separate,
  functioning system whose only effect is command breadth; changing it is
  its own story.
- Aptitude. It stays innate.
- Trait acquisition on promotion — [S10](s10-trait-mechanics.md).

## Acceptance

- Two marines on the same mission with materially different contributions
  receive materially different XP.
- No single-soldier farming strategy outperforms spreading experience
  across a fireteam by more than a stated factor.
- Deterministic under replay for a frozen outcome.
- Thresholds re-derived so the Green-to-Elite arc spans a stated number of
  missions. Name the intended arc length in the story record — the current
  implicit answer is ~16 missions and nobody chose it.
- Existing scenarios re-verified for force balance.

## Open questions

- **Fifth tier above Elite?** Leaning yes for a long campaign, but it needs
  a name and a reason to exist beyond "more". Something that changes
  *behavior* rather than multipliers — an elite marine who reloads under
  cover, or holds fire at bad odds — would be a better capstone than
  another 5% accuracy.
- Should XP also flow to marines who were **stationed** rather than
  deployed? Named stationing already produces incidents and casualties.
  Leaning: yes, at a low garrison rate, so a stationed team is not frozen
  in time.
