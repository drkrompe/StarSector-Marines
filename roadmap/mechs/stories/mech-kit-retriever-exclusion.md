# Story — Exclude mechs from infantry kit retrieval

Status: PROPOSED

Written: 2026-08-24

Read `mechs-nouns.md` before changing this story.

## Problem

`EquipmentDropSystem.nearestAvailableMarine` selects a retriever by MARINE
faction without excluding live mechs. A mech can therefore receive
`KIT_RETRIEVER` and enter `KitRetrieverBehavior`, which owns infantry movement,
cooldown, and fire-intent behavior rather than mech doctrine and weapons.

## Authority boundary

`MECH_LOADOUT` membership is the authoritative live-mech capability. Mechs owns
that classification boundary; infantry equipment recovery owns which eligible
ordinary marine receives a dropped kit. Selection must use capability
membership rather than infer actor kind from faction, unit type, or rendering.

## Scope

- Exclude `MECH_LOADOUT` actors from kit-retriever eligibility.
- Preserve nearest eligible ordinary-marine selection and the existing busy
  checks for planters and retrievers.
- Leave a drop unassigned when no eligible infantry marine exists.
- Add focused coverage for mixed mech/marine and mech-only cases.

## Acceptance

- With a nearer live mech and an eligible marine, the marine receives
  `KIT_RETRIEVER` and the drop target; the mech receives neither.
- With only live mechs available, no `KIT_RETRIEVER` or retriever drop task is
  assigned; no mech enters `KitRetrieverBehavior` or receives its path,
  cooldown tick, or fire intent.
- Existing infantry retrieval and planting behavior remains unchanged.
- Future mech chassis inherit the exclusion through `MECH_LOADOUT` membership.

## Out of scope

- Redesigning equipment-drop ownership, kit payloads, planter policy, mech AI,
  or weapon behavior.
- Changing faction eligibility for ordinary infantry.
- Campaign or Fleet Armory inventory work.

## Dependencies

None. This cleanup is independent of the manual specialist-strider tuning in
`s1-specialist-striders.md`.
