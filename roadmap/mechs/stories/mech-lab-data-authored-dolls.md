# Story — Load Mech Lab fitting dolls from data

Status: PROPOSED

Written: 2026-08-25

Read `mechs-nouns.md` before changing this story.

## Problem

The Mech Lab now treats each asset's fitting doll as one coherent definition,
but Bulwark, Hound, and Sirocco definitions are still constructed in Java. New
mechs and future tanks therefore require a code edit to author maintenance
facing, physical leader anchors, surrounding equipment docks, footprints,
socket types, and capacities. That makes visual fitting data harder to iterate
and creates pressure for vehicle-specific renderer conditionals.

## Authority boundary

The fitting-doll catalog owns maintenance presentation and socket geometry for
an asset class. The referenced battle appearance remains the authority for the
ordered visible layers and physical scale. Campaign mech ownership and refit
commands remain the authorities for installed equipment, inventory, budgets,
and mutation. Loading a doll definition must not create a second loadout or
permit the UI to bypass those commands.

## Scope

- Define a versioned external fitting-doll catalog keyed by stable mech or
  vehicle asset identity.
- Author maintenance facing and every socket's stable id, compatibility type,
  capacity, physical mount anchor, remote equipment-dock center, interaction
  footprint, and factory-lock state.
- Load and validate the catalog at application startup, with actionable errors
  for missing asset references, duplicate socket ids, non-finite coordinates,
  invalid capacities or footprints, and unsupported compatibility values.
- Migrate Bulwark, Hound, and Sirocco without changing their current rendered
  appearance, relative battle scale, socket selection, or fitting geometry.
- Make the Mech Lab resolve dolls through the catalog so a later heavy vehicle
  can use the same renderer without a chassis-name branch.
- Add focused loader, validation, and migrated-layout parity coverage.

## Acceptance

- Bulwark, Hound, and Sirocco load exclusively from external doll definitions;
  removing or corrupting a required definition fails clearly during catalog
  preparation rather than silently substituting a generic doll.
- Every migrated socket preserves its current leader endpoint, equipment-dock
  position, footprint, capacity, type, occupancy semantics, and lock state.
- The renderer receives one validated fitting-doll value and contains no
  per-variant mount-coordinate or maintenance-facing switch.
- A test-only vehicle-shaped definition with a different facing and socket set
  parses and projects through the same catalog and geometry path.
- Battle appearance order and asset-relative physical scale remain sourced from
  the ordinary battle appearance authority.

## Out of scope

- Implementing drag-and-drop refit mutation, equipment inventory, salvage,
  chassis budgets, or new component content.
- Replacing the battle appearance/layer catalog or duplicating its sprite data
  inside the fitting-doll catalog.
- Shipping a production tank or wheeled-vehicle fitting screen.
- A general visual editor for doll anchors and docks; the data contract should
  remain compatible with adding one later.

## Dependencies

The current `MechFittingLayout.DollDef` boundary and shared battle composition
are the migration source. This story is independent of specialist battle tuning
in `s1-specialist-striders.md`.
