# T3 endgame nouns

Status: ACTIVE

Written: 2026-08-23

## Purpose

The T3 endgame is the campaign tier's sole controlled crossing from political
simulation into Starsector's durable faction and market state. It turns a
resolved civil war into a replay-safe faction transition, then—only for a
decisive, attributed player victory—into private moral correspondence.

`living-world-nouns.md` owns rank, ambition, political-chain vocabulary, and
the player's choices while a civil war is open. `moral-compass.md` owns the
hidden record of those choices. This feature owns the irreversible handoff and
its epilogue, not a second political simulation.

## Vocabulary

- **Civil war** is the closed political struggle that may produce a claim; it
  does not itself mutate vanilla state.
- **Throne claim** is the persisted, source-unique handoff from a resolved
  civil war to the endgame boundary. It records the claimant, incumbent,
  target market, result faction, and any already-attributed player role.
- **Prepared claim** is a valid handoff awaiting the external write. It is
  retryable when the live sector is temporarily unavailable.
- **Applied claim** is a handoff whose market transition has been verified and
  whose local political result is final. **Failed claim** is a rejected or
  invalid handoff; it never receives a partial local promotion.
- **Claimant League** is the one predeclared result-faction identity used by
  successful claims. It is not a runtime-generated faction per house.
- **Diplomatic rupture** is the separate post-ownership hostility adjustment
  between the former ruler and the Claimant League.
- **Kingmaker testament** is an immutable private correspondence unlocked only
  by a decisive, attributed claimant victory. It judges remembered acts; it is
  neither a reward nor an alignment dashboard.

## Laws

1. Only the T3 endgame consumer may write campaign consequences into vanilla
   market ownership, entities, or faction diplomacy. Other campaign features
   prepare and read claims only.
2. A claim is source-unique and check-before-write. If the recorded external
   result is already true, local state is repaired without replaying the
   mutation. A transient external failure leaves the claim prepared; an invalid
   handoff fails cleanly.
3. Ownership, local promotion, and diplomatic rupture are distinct outcomes.
   Ownership must settle before diplomacy; an unrecoverable diplomacy failure
   never rolls back an applied faction transition.
4. A successful claim promotes its claimant through the political model and
   clears the completed ambition. The claimant's market and its associated
   sector entities agree on the recorded result faction.
5. Autonomous outcomes are player-reputation neutral. Only explicitly
   attributed civil-war participation can change the two involved houses'
   reputation, exactly once; it never changes MRB reputation or contract
   counters.
6. Public Chronicle history reports a verified faction transition, never a
   merely prepared claim.
7. A testament freezes the qualifying victory's identities and the available
   prefix of moral history. Later choices cannot alter its evidence, voice, or
   verdict. Missing or contradictory history fails closed rather than invented
   prose.
8. Reading or reconstructing a testament may reveal correspondence, but cannot
   produce another political consequence, reward, or moral choice.

## Flow

An eligible civil war closes into one throne claim. The T3 consumer validates
that frozen handoff against the current campaign and sector state, then applies
or repairs the market transition. Once ownership is applied, it independently
settles the diplomatic rupture and the campaign layer records the public
Chronicle fact.

Separately, an attributed claimant victory that meets the decisive kingmaker
threshold may seal one testament. The sealed snapshot can cite only the moral
history that existed at that moment. The Last Testament correspondence presents
that fact-bound judgment when it can be rendered, then preserves it as history.
The testimony does not wait on diplomacy: a recovered ownership result and a
real player choice remain meaningful even if the relationship port cannot
settle.

## Boundaries and extension

The campaign framework provides persistence and system ordering through
`campaign-framework-nouns.md`. T3 does not create ordinary offers, decide
political ambition, or expose moral values. New civil-war content must preserve
the source-unique claim handoff and explicit player attribution. New testament
source families may extend the editor only when their historical facts can be
frozen and validated without exposing hidden arithmetic.

The only live work is `last-testament-live-acceptance.md`.
