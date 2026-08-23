# Campaign nouns

Status: ACTIVE

Written: 2026-08-23

## Purpose

The campaign tier is the persistent consequence layer around operations. The
player is a named mercenary company working inside the political space beneath
Starsector's faction graph, not a new faction that silently replaces it.
Campaign play makes the company's commitments, relationships, and consequences
survive the battle that produced them.

## Campaign shape

An **operation** is the playable tactical work. A **contract** is the
commercial commitment that can authorise an operation. A **political chain** is
the longer-running house conflict that contracts may advance or oppose. A
**campaign event** is an exceptional, cost-shaped choice that is not a second
contract. These are distinct authorities: a battle result reports facts; its
owning campaign policy decides what those facts settle.

The campaign records durable identities, accepted commitments, observed
outcomes, and attributed consequences. It must not infer a political result,
moral choice, or recovery right merely because an operation ended. Each such
consequence requires its own persisted authority and exactly-once boundary.

## Political scale

The company normally acts through houses and local interests within a vanilla
faction. This allows frequent local political change without pretending every
operation is a faction war. As consequences become broader, their authority
must become more explicit and more expensive: local influence and commercial
standing remain campaign-owned; exceptional faction ownership and diplomacy
cross only through `t3-endgame-nouns.md`.

The player remains a company even when its work materially reshapes the world.
MRB credibility is cross-client company standing; house relationships are local
political history. Neither is a substitute for vanilla faction reputation.

## Ownership

`architecture.md` owns the persistent-runtime contract.
`campaign-framework-nouns.md` owns monotonic time and ordered autonomous
execution. `living-world-nouns.md` owns houses, stakes, ambition, political
chains, and learned Chronicle truth. `contracts-nouns.md` owns agreements and
settlements; `loot-nouns.md` owns recovery; `campaign-event-nouns.md` owns
exceptional event lifecycle; `mission-tier-nouns.md` owns scale vocabulary;
and `narrative-nouns.md` owns truthful presentation.

`themes.md`, `economy.md`, and `backgrounds.md` are direction for future
campaign work. `moral-compass.md` owns the hidden record that can connect
otherwise separate campaign choices without turning it into an optimisation
surface.

## Laws

- A campaign extension must name its authoritative persisted fact, its terminal
  boundary, and the feature that may change it.
- A player-facing screen projects campaign truth; it does not become a parallel
  simulator, event queue, or settlement path.
- Local campaign simulation never directly escalates into vanilla faction
  ownership or diplomacy. That exceptional boundary belongs only to T3.
- New content may add a contract, chain, event, or authored operation, but must
  not blur their commitment, reward, and resolution rules.
