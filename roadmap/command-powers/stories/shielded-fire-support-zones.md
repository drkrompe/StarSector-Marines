# Shielded fire-support zones

Status: PLANNED — depends on `hard-installation-first-map-feature.md` publishing an operational relay and coverage footprint.

Written: 2026-08-24

Read `command-powers-nouns.md` and `campaign-battle-bridge-nouns.md` before
implementing this story.

## Problem

Orbital Barrage currently validates its ordinary target and resource contract
without any terrain-authored defense envelope. A committed capital ship can
therefore strike the decisive ground objective even when the target market has
a planetary shield. Higher generic defense does not explain why ground troops
must take the site.

## Goal

An active local shield relay denies Orbital Barrage inside its authored
coverage footprint. Ground forces must capture the relay's host compound or
destroy the relay before the fleet can fire into that protected area.

This is local denial, not global removal of the player's power. Targets outside
all active shield footprints remain legal, and the command deck still contains
the committed barrage throughout the battle.

## Activation contract

- Targeting presentation queries the same simulation-owned shield coverage
  that final activation validation uses. Shielded cells are visibly marked and
  return a specific denial reason.
- A request inside active coverage does not spend command points, supplies, a
  charge, or cooldown. Final validation repeats the coverage check so a stale
  UI sample cannot bypass a newly reactivated relay.
- DEFENDER_HELD and CONTESTED host compounds keep an intact relay operational.
  MARINE_HELD or structural destruction removes its coverage immediately.
  Recapture reactivates only an intact relay.
- A barrage already committed before a relay legally reactivates resolves from
  its committed activation; coverage does not retroactively cancel paid fire.
  The relay state is checked at the existing request-and-commit boundary.
- Mission and contract objectives remain their own authority. Barrage damage
  may destroy ordinary structures, but it does not silently satisfy capture,
  recovery, or defend mandates.

## Presentation

Briefing names the local shield restriction before deck selection. In battle,
the barrage targeting mode shows the protected footprint and names the relay
that must be neutralized. Ordinary battle view may use a restrained field-edge
or relay-status treatment, but it must not reveal terrain or enemies through
fog merely to explain the restriction.

## Acceptance

- A barrage inside active coverage is rejected with no resource or pacing
  change; an otherwise equivalent target outside coverage commits normally.
- Capturing the host compound intact opens the protected cells without
  destroying the relay; defender recapture closes them again.
- Destroying the relay opens the cells irreversibly for that battle.
- Targeting preview and simulation commit agree across capture, contest,
  recapture, and destruction transitions.
- Missions without a shield relay preserve the shipped Orbital Barrage path and
  seeded battle behavior.
- Live acceptance confirms the restriction explains why the ground push is
  necessary without making the player's committed fleet source feel removed or
  broken.

## Out of scope

- Planet-wide shield simulation, multiple overlapping relay networks, or
  shielding against ground-fired projectiles.
- Blocking marine insertion, fighters, resupply, or mech shuttles. Craft risk
  remains `s6-drop-geography.md`.
- Orbital battery standoff — `orbital-battery-fire-support-lock.md`.
- Campaign damage to the planetary-shield industry or player fleet.
- Contract penalties and intact recovery, owned by
  `faction-ground-contract-policy.md` and `intact-installation-recovery.md`.
