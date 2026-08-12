# Slice F1 — Autonomous promotion chains

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `0a00ceb0`, `156ac5b1`, `a4cd42c8`

## Goal

Give Tier-1/2 `PROMOTE` ambitions a distinct autonomous route up the existing
rank ladder without borrowing the horizontal consolidation plot or crossing the
Tier-3 faction-write boundary.

## Locked rules

- `PROMOTE` is appended to the persisted `ChainArchetype` vocabulary.
- A monthly promotion scheme requires an ACTIVE Tier-1/2 actor, a valid
  next-rank ambition target, no other active chain, and no strict home-market
  majority. Majority houses already have the organic promotion route.
- The chain binds the strongest ACTIVE same-faction rival on a contested actor
  home-market industry. Ties choose lowest industry id, then lowest house id.
- Promotion schemes run for 60 daily progress with discovery risk 64.
- Resolution seizes 20 bound-industry share, grants 90 actor promotion progress,
  and suppresses 30 rival progress. Shared `HousePromotion` supplies flooring,
  rank crossing, and remainder carry.
- If another route promotes the actor first, the chain resolves without
  replaying its payload. Invalid participants, location, faction, or tier fail
  exactly once.
- Generic discovery, Chronicle, intervention offer, and intervention resolution
  paths already consume chain identity rather than archetype-specific payloads,
  so they apply without parallel mutation logic.

## Automated verification

- `AutonomousChainCreationSystemTest` covers same-faction filtering,
  deterministic location choice, majority exclusion, and invalid targets.
- `ChainAdvancementSystemTest` covers the full +90 / -30 / 20-share payload,
  exact replay prevention, already-achieved resolution, and invalid-faction
  failure.
- Full `gradlew build` passes.
