# Campaign contracts

Status: ACTIVE

Written: 2026-08-23

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
- A **mission contract** is resolved through one or more player operations.
  Strike and Escort are single-operation work; Planetary Assault is a phased
  sequence whose non-terminal setback can require another attempt.
- A **stationing contract** is a time-bounded local commitment. Garrison holds
  a detachment ready for a defense; Cadre holds it for training and possible
  incidents. The retainer pays for availability, not for an automatic victory.
- A **response** is the player decision owed when a pending stationing defense
  or incident is armed. It may be fought through the established response
  route, held for later while time remains, or explicitly written off.
- **Recovery** is the system-generated follow-on to an employer-breached
  stationing contract. It is not a sixth patron-offered work type.
- A **settlement** is the one terminal contract outcome: completed, failed,
  employer-breached/defaulted, abandoned, or expired before acceptance.

## Shape and authority

The standard offer surface serves eligible active Tier 1–3 patrons. Current
standard generation gives Tier 1 Strike work; Escort and stationing work begin
at Tier 2; Planetary Assault is Tier 3 work. Tier 4 is outside the ordinary
contract surface and belongs to `t3-endgame-nouns.md`. A patron's eligibility
combines its standing as an active house with the player's house relationship
and MRB credibility; no individual offer invents a parallel gate.

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
   bounded both per patron and globally. Eligibility is shared policy for
   generation and acceptance.
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
6. Player-event presentation is a projection of persisted pending work, not a
   second event store. A notice is acknowledged only after presentation and may
   reappear only at its defined urgency; resolving the underlying work removes
   the notice everywhere.
7. Employer breach/default is distinct from player abandonment and battle
   failure. It leads to recovery of stranded stationing personnel rather than
   silently returning them or treating the breach as player fault.

## Flow

1. An eligible patron makes a bounded offer. The player lets it expire or
   accepts terms.
2. A mission contract produces operations until its terminal result. A
   stationing contract binds personnel, pays for its committed duration, and
   may arm a local response.
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

The remaining work is listed in `stories.md`. Completed slices are recorded in
`shipped.md`.
