# Campaign contracts

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-09-02 — named stationed strength and the posting; a vanilla-triggered defence now settles from vanilla's own result when unanswered.

Campaign contracts turn the player's relationship with a patron into a bounded
piece of work. They sit between the campaign's political chains and an
operation: a chain supplies strategic context, a contract fixes the commercial
commitment, and an operation supplies the playable resolution. Contracts do
not own the political simulation, battle presentation, cargo, or narrator.

## Vocabulary

- A **patron** is the active house offering and paying for work. A **target** is
  the other party or protected location the work concerns; stationing may have
  no opposing house target.
- An **offer** is a time-limited proposed contract. It becomes an **accepted
  contract** only when its eligibility and terms are satisfied; an ignored offer
  expires without becoming work owed by either side.
- **Terms** are the accepted economic and operational commitments: the work
  kind, payout shape, salvage entitlement, and—where applicable—the committed
  detachment and duration. Terms establish a right to recovery; they do not
  create or choose the recovery manifest. That belongs to `loot-nouns.md`.
- An **objective mandate** is the term that says what the employer requires to
  happen to a mission asset: defend it, capture it intact, recover something
  from it, disable it, or destroy it. A related collateral policy may restrict
  damage beyond the named target. Mission type still supplies tactical shape;
  it does not silently choose the mandate.
- An **objective result** is the operation-frozen fact describing how that
  named asset ended: preserved, captured intact, disabled intact, destroyed, or
  not secured. The owning mission freezes the fact; contract settlement tests
  it against the mandate, while loot may consume the same immutable fact for
  recovery eligibility.
- A **mission contract** is resolved through one or more player operations.
  Strike and Escort are single-operation work; Planetary Assault is a phased
  sequence whose non-terminal setback can require another attempt.
- A **stationing contract** is a time-bounded local commitment. Garrison holds
  a detachment ready for a defense; Cadre holds it for training and possible
  incidents. The retainer pays for availability, not for an automatic victory.
- A **response** is the player decision owed when a pending stationing defense
  or incident is armed. It may be fought through the established response
  route, held for later while time remains, or explicitly written off.
- **Stationed strength** is what a Garrison detachment is worth to the place
  it holds while the player is elsewhere: a contribution to the protected
  market's vanilla ground defences, sized by its living seats and experience
  standard, present for exactly the term. It is the thing the retainer buys.
- A **posting** is stationing without a patron: the player's own detachment
  bound to one of the player's own markets, with no retainer, no term, and no
  commercial consequence. It shares the response and settlement machinery as
  a row whose employer is nobody; `meta-progression.md` owns why the polity
  is never a client.
- **Recovery** is the system-generated follow-on to an employer-breached
  stationing contract. It is not a sixth patron-offered work type.
- A **settlement** is the one terminal contract outcome: completed, failed,
  employer-breached/defaulted, abandoned, or expired before acceptance.

## Shape and authority

The standard offer surface serves eligible active Tier 1–3 patrons. Current
standard generation gives Tier 1 Strike and Escort work; stationing begins at
Tier 2; Planetary Assault is Tier 3 work. Tier 4 is outside the ordinary
contract surface and belongs to `t3-endgame-nouns.md`. A patron's eligibility
combines its standing as an active house with the player's house relationship
and MRB credibility; no individual offer invents a parallel gate.

An ordinary mission offer also persists its authored operation tier alongside
type, patron, target, risk, and commercial terms. Tier 1–3 patrons normally map
to First Contract, Established, and Veteran scale; Planetary Assault raises
that result to its Reinforced floor. The accepted operation consumes the stored
scale, so later political changes or risk interpretation cannot resize work the
player was already shown.

Standard supply is geographically sparse. An origin market may hold at most one
open offer, and the markets in one star system may hold at most three between
them; the per-patron and sector-wide ceilings remain additional backstops. These
are ceilings rather than quotas: a market or whole system may legitimately have
no work. System membership is read from the live sector when autonomous policy
runs, while the offer row remains the persisted authority. Opening a planet or
switching clients projects those rows and authored exceptions; it never creates
ordinary work from the industries visible on that screen.

The contract feature owns the agreement and its outcome.
`campaign-framework-nouns.md` owns the monotonic campaign day and ordered
autonomous-system seam on which offers, retainers, defaults, incidents, and
deadlines depend. `living-world-nouns.md` owns house rank, political power,
and chain policy. A contract may advance or oppose that larger political work
through its resolved outcome, but it does not own the political model.

`loot-nouns.md` owns recovery manifests, claim selection, and transfer into
cargo. Contract terms establish the negotiated entitlement and operation
resolution freezes it; neither a later fleet change nor the loot picker can
rewrite what the contract promised. `narrative-nouns.md` owns how a patron and
the result are remembered or described. Personnel ownership and availability
remain with the roster; a contract records a commitment rather than becoming a
second roster.

## Laws

1. An offer is not a live obligation. It is accepted once or expires as a
   terminal tombstone; it does not re-enter the offer pool as a different row.
   Ordinary mission acceptance is a persisted pre-launch boundary: it locks the
   accepted terms, records the commitment, and removes offer-expiry authority
   before the operation can resolve.
2. Standard offers are deterministic for the same campaign day and patron, and
   bounded per patron, origin market, star system, and sector. Those bounds are
   scarcity ceilings, not refill targets. Eligibility is shared policy for
   generation and acceptance. Mission scale is fixed in the persisted offer,
   not reconstructed from risk when the operation launches.
3. A mission result advances only its owning contract. A terminal settlement
   applies the corresponding patron/MRB consequence at most once, even if a
   resolver or campaign frame is replayed.
4. Stationing reserves the named detachment and bound captain for its term.
   Their availability is restored only through the appropriate release,
   completion, failure, abandonment, or recovery path; a screen must not
   bypass that ownership boundary.
5. A pending stationing response is real unpaid work. Term expiry cannot turn a
   live defense or incident into success. A response has one persisted deadline
   on campaign day, and an unanswered response settles as a failure with the
   same domain path whether the player chooses to write it off or lets it lapse.
   The one exception is a defence armed by a vanilla raid: the stationed
   detachment fought that raid through vanilla's own strength ratio whether or
   not the player came, so an unanswered vanilla-triggered defence settles
   from vanilla's result — held, held with losses, or overrun — graded on the
   raid effectiveness computed with the detachment counted.
11. A stationed detachment's strength reaches vanilla through one attributable,
    replay-safe modifier on the protected market's ground defences, applied
    for the term and removed with it. A won vanilla-triggered defence ends the
    raid only through vanilla's own abort or fail path. Neither write touches
    market ownership, stability, or industries directly; those remain
    vanilla's to settle.
6. Player-event presentation is a projection of persisted pending work, not a
   second event store. A notice is acknowledged only after presentation and may
   reappear only at its defined urgency; resolving the underlying work removes
   the notice everywhere.
7. Employer breach/default is distinct from player abandonment and battle
   failure. It leads to recovery of stranded stationing personnel rather than
   silently returning them or treating the breach as player fault.
8. Offer generation establishes target fit before weighting flavor. The
   employer/target relationship and the target's actual assets decide which
   work is admissible; faction identity may weight and describe that eligible
   pool but may not make a nonsensical target valid.
9. Destruction of employer-owned productive or civic infrastructure is an
   authored exception with a stated denial or containment reason, never a
   routine consequence of rolling Strike work.
10. Settlement evaluates the accepted mandate against frozen objective results.
    Contracts do not inspect the ended simulation, and presentation or loot may
    not reinterpret a result to change success.

## Flow

1. An eligible patron makes a bounded offer. The player lets it expire or
   accepts terms.
2. A mission contract produces operations until its terminal result. A
   stationing contract binds personnel, pays for its committed duration,
   contributes its stationed strength to the protected market, and may arm a
   local response — from the mod's own political pressures or from a vanilla
   raid of either shape on that market.
3. Operation or stationing policy writes one settlement. The result updates the
   agreement's relationship and credibility consequences, and may inform the
   political layer.
4. A victorious operation hands its frozen entitlement to loot; a breached
   stationing agreement can hand stranded personnel to recovery. Those adjacent
   flows own their own presentation and completion rules.

## Extension rules

New work kinds must choose mission or stationing semantics before adding a UI:
the choice determines commitment, cadence, payment, failure, and response
authority. New stationing event sources may join the notice projection only by
exposing a persisted pending payload and the same deadline/settlement contract.
Do not add type-specific reputation or duplicate launch paths around shared
eligibility, response, and settlement policy.

Industry-bound archetypes are offer-candidate content, not an alternative
planet-local supply. Future faction policy may select a valid real asset from
that catalog and persist it in an offer, but a UI consumer must never enumerate
the catalog to manufacture work on demand.

`faction-ground-contract-policy.md` owns the planned vertical that adds
objective mandates, asset/relationship admissibility, faction weighting, and
briefing disclosure to production offers. Those additions extend terms and
shared eligibility; they do not create a parallel faction contract system.

The remaining work is listed in `stories.md`. Completed slices are recorded in
`shipped.md`.
