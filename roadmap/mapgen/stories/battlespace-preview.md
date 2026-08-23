# Battlespace preview

Status: PARKED

Written: 2026-08-23

Read `mapgen-nouns.md` before reviving this cross-feature story.

## Activation gate

The briefing/UI design chooses an honest pre-battle map preview and names what
the player can learn or select from it.

## Goal

Generate or replay the actual mission battlespace before battle entry so the
briefing can present trustworthy terrain, objectives, defenses, and possible
landing geography. The preview and battle must share one persisted generation
identity; a decorative approximation must not imply false tactical knowledge.

## Acceptance

- Briefing and battle resolve the same seed, map family, and target profile.
- The chosen preview presentation has a measured generation/render cost.
- Recon gating and any landing-zone interaction have explicit UI owners.
- The existing briefing remains complete when this parked feature is absent.

## Boundary

Mapgen owns deterministic replay of the generated world. Briefing presentation,
recon policy, and player landing selection belong to their owning features.
