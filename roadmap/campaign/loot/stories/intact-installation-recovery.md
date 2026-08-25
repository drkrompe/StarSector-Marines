# Intact installation recovery

Status: PLANNED — depends on objective mandates, frozen installation outcomes, and the S5/S6 parts-and-schematic authorities.

Written: 2026-08-24

Read `loot-nouns.md`, `contracts-nouns.md`, and `progression-nouns.md` before
implementing this story.

## Problem

The recovery manifest can flavor candidates from mission, faction, and industry
context, but it cannot distinguish a shield relay captured intact from one
destroyed by demolition or orbital fire. If both produce the same generic haul,
there is no economic reason to preserve a valuable installation and no honest
gate for faction-specific fabrication schematics.

Conversely, capturing an employer-owned facility intact must not imply that the
mercenary company may strip it. Contract mandate, negotiated salvage rights,
and asset ownership still matter.

## Goal

Freeze one objective-conditioned recovery fact for each relevant installation,
then let the recovery catalog use those facts to admit site-specific candidates
without changing entitlement or inventing individual drops.

The first terminal vocabulary is:

- PRESERVED — a defended friendly asset remains operational and under the
  required control;
- CAPTURED_INTACT — hostile asset secured through its owning objective/compound;
- DISABLED_INTACT — capability neutralized without structural destruction;
- DESTROYED — structure irreversibly destroyed;
- NOT_SECURED — objective remained hostile, contested, abandoned, or otherwise
  outside the winning recovery perimeter.

Each fact carries a stable site id, installation kind, target-faction
provenance, accepted objective mandate, terminal state, and whether the
contract grants any recovery right over the site. Mission resolution freezes
the fact; loot never queries the live battle or reconstructs it from surviving
entities.

## Recovery policy

- CAPTURED_INTACT is the primary gate for intact equipment stock, advanced
  components, and rare faction-provenance schematics.
- PRESERVED normally returns an employer-owned facility with no player recovery
  pool unless accepted terms grant a separate allotment or reward.
- DISABLED_INTACT may admit technical components or data appropriate to the
  mission, but does not automatically grant the full captured-site pool.
- DESTROYED admits only bounded scrap/common-parts recovery unless an authored
  candidate explicitly survives destruction. Pristine schematics and advanced
  control assemblies do not.
- NOT_SECURED contributes no installation-specific pool.
- A CAPTURE_INTACT or DEFEND mandate for an employer-owned/future-owned site
  normally returns the facility to the employer. It enters player recovery only
  when accepted terms explicitly grant a stock allotment, schematic reward, or
  salvage right.
- A RECOVER mandate whose payload is contract property settles through the
  mission/contract reward unless terms also make it a loot choice. Do not show
  the same datacore or schematic as both mandatory delivery and optional
  salvage.

All admitted candidates still enter the deterministic manifest and selection
budget. Intact capture creates eligibility, not automatic ownership.

## Progression integration

- Ordinary recovered stores remain base-game cargo owned by the loot manifest;
  progression does not convert them into a second parts currency.
- An equipment-template candidate uses the stable identity and faction-provenance
  rules from `s6-unlock-ladder-expansion.md`. Claim settlement collects that card
  at most once; an already-known card receives an explicitly authored duplicate
  value rather than disappearing or unlocking twice.
- A Hegemony, Tri-Tachyon, League, Church, Path, Diktat, pirate, or Independent
  installation biases only candidates that its recovery catalog declares. A
  faction id is not permission to synthesize any item associated with that
  faction.

## Acceptance

- Identical mission/contract facts with PRESERVED, CAPTURED_INTACT,
  DISABLED_INTACT, DESTROYED, and NOT_SECURED produce deterministic, visibly
  different eligible pools.
- Destroying a relay or battery can never yield its pristine schematic/control
  candidate; capturing it intact does not guarantee that candidate beyond the
  catalog's explicit rarity and entitlement rules.
- Employer-owned or capture-for-client facilities yield no player site loot
  without an explicit accepted recovery grant.
- Reopening Results cannot reroll terminal site facts or candidate selection,
  and repeated settlement cannot pay components or unlock a recipe twice.
- Existing missions without installation facts preserve their current recovery
  behavior.
- A live pass demonstrates the intended decision: destruction is the fast
  tactical denial, while intact seizure preserves a possible company reward.

## Out of scope

- Deciding the objective mandate or collateral policy —
  `faction-ground-contract-policy.md`.
- Facility placement, coverage, capture, or destruction mechanics.
- Campaign ownership/disruption writeback for vanilla industries.
- Bypassing the recovery picker with automatic battlefield pickups.
- Repricing the overall parts economy or equipment ladder.
