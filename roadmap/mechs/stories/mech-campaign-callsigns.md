# Mech campaign callsigns

Status: IN PROGRESS

Written: 2026-10-02

Updated: 2026-10-02 — bounded callsign length and defined the frozen battle-name flow.

Read `mechs-nouns.md` before implementing this story.

## Goal

Let the player name a persistent campaign mech in the Mech Lab and recognize
that callsign after the selected support payload lands. The callsign is a
display label carried by the deployment snapshot, not a replacement for stable
campaign or battle identity.

## Scope

- Edit the callsign of a player-owned campaign mech from its Mech Lab surface.
- Keep the current default name when the player has not renamed the chassis.
- Carry the callsign through the frozen mech-support deployment values and use
  it for the landed unit's visible identity.

## Constraints

- A callsign belongs to one campaign mech record. It does not change that
  record's stable id, chassis, role, components, squad membership, or inventory.
- Callsigns need not be unique. Stable ids, never display text, distinguish
  records and battle entities.
- Empty or whitespace-only input is refused without changing the current name.
  Accepted input is trimmed before it is stored and contains at most 48
  characters.
- Deployment copies the callsign at commitment. The battle does not read or
  write the campaign record, and later campaign edits cannot rename a mech
  already in flight.
- Older or non-campaign deployment values with no callsign retain their
  existing deterministic fallback identity.

## Acceptance

- A player can edit the selected campaign mech's callsign in the Mech Lab and
  sees the new value on the selected asset and its roster entry.
- The edited value survives campaign save and load through the existing
  campaign-mech persistence path.
- Blank input and failed rename commands preserve the previous callsign and all
  hardware, role, stable-id, and inventory state.
- A committed support payload freezes the callsign. After landing, the mech's
  selection and battle identity presentation show that value without changing
  battle entity identity or campaign state.
- Legacy/debug deployment inputs without callsigns still receive their prior
  fallback names; unrelated battle actors keep their current naming behavior.

## Out of scope

Creating or organizing multiple persistent lances, changing the four-chassis
per-squad bound, deciding the shared two-to-eight lance cap, and defining
casualty, salvage, or replacement rules. Those remain part of the broader Mech
lance-company gate in `v0.1-alpha.md`.
