# G18 — Planetary Assault contract negotiation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `e404b8a9`

## Goal

Freeze salvage-for-cash terms at the first Planetary Assault deployment and
carry them consistently through every later phase.

## Locked rules

- Mission data distinguishes contract-wide salvage terms from the current
  phase's capped entitlement.
- First-phase briefing adjusts the 80% contract claim and shows both contract
  claim and current-phase entitlement.
- First deployment atomically freezes negotiated salvage and cash multiplier
  onto the contract row using the standard `100 + trimmed/2` curve.
- Later phases regenerate from the frozen terms and render negotiation read-only.
- A later-phase launch is rejected if its mission terms do not match the
  persisted contract, preventing stale or manipulated dossiers from deploying.
- Ordinary one-shot missions keep their existing single-layer negotiation data.

## Automated verification

- `PlanetaryAssaultTermsTest` covers first-deployment freeze, later-phase
  validation, closed negotiation, invalid curves, and non-assault rejection.
- `PlanetaryAssaultPhaseTest` continues to cover phase-cap application.
- Full build green.
