# Retire risk-to-tier bridges

Status: PROPOSED

Written: 2026-08-23

Read `mission-tier-nouns.md` before implementing this cleanup story.

## Goal

Once every real mission has an explicit tier, remove risk-derived tier and map
scale compatibility entry points so a new production path cannot silently
recombine the axes.

## Acceptance

- Mission factories, including event and authored paths, provide an explicit
  tier or a declared fixed tier.
- Fixed-map and special opening-operation paths are either made tier-aware or
  documented as deliberate exceptions with explicit scale authority.
- Battle setup consumes mission tier for real missions; headless fixtures name
  any intentional tier directly.
- The risk-to-tier and risk-to-map-scale compatibility APIs are deleted along
  with obsolete overloads and documentation.
- Tests prove type floors and tier-based scale still hold without fallback
  derivation.

## Dependency

`tiered-production-offers.md` must establish production tier authority first.
