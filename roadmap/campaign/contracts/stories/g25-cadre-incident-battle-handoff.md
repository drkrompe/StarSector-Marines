# G25 — Cadre incident battle handoff

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make pending Cadre incidents playable without routing their personnel through
the ordinary fleet detachment or contract-completion paths.

## Locked rules

- Assignment management exposes a Respond action for pending incidents.
- Incident missions carry a stable contract/due-day/archetype key and use the
  assigned `GARRISONED` captain.
- Required drops scale from committed stationing marines and are delivered by
  local Cadre transports; fleet shuttles, fighter wings, and ship-sourced powers
  are not consulted.
- Factory accidents and defector leads use Extraction battles; live-fire raids
  use Assault. Cadre incident salvage remains the designed 5%.
- Result application validates the incident key before any side effects, never
  removes fleet-cargo marines, and routes the outcome through
  `StationingIncidentResolution` instead of completing the parent Cadre.

## Automated verification

- `StationingIncidentMissionKeyTest` covers key round-trip and malformed input.
- `StationingIncidentMissionFactoryTest` covers mission mapping, local drop
  scaling, stationing source identity, salvage, and detachment transport cycles.
- Existing incident-resolution tests cover exactly-once campaign writeback.
