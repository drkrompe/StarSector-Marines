# Boarding deck missions

Status: PARKED — gated on a mission model that owns boarding objectives.

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story. Depends on
`ship-deck-family.md` and `facility-room-themes.md`; it does not depend on the
company ship adoption or upgrade stories.

Generate hostile prize decks for boarding. By law 8 this is the same generator
producing the same family with different parameters — hostile faction, a garrison,
a real threat policy, and compartment choice driven by the campaign-resolved
target rather than by owned facilities.

## Why this is parked

Ship interiors supplies a deck, a breach point, and a longitudinal gradient. It
does not own what the mission is *for*: objectives, win conditions, extraction,
reward, and what happens to a ship that is successfully taken. Those belong to a
mission model that does not exist yet. Contract that first; this story is the
map half only.

## Sketch

- Prize deck parameters resolved from a campaign target's class and role, in the
  shape the existing target profile already uses to carry campaign facts into
  generation without importing campaign types.
- Objective selection by end: the command spaces forward or the engineering
  spaces aft. One deck yields two distinct fights depending on which end is
  wanted, and the choice sets the depth gradient the defenders fortify against.
- Defender placement reading published topology roles — bulkhead order,
  articulation, depth from the breach — rather than coordinate heuristics. This
  is the same consumer `station-role-placement.md` wants for stations, and the
  two should share it rather than each grow a private one.
- Multiple breach points as a pincer along the axis, which is a different problem
  from a station's cardinal ports converging on a core.

## Open questions

- Does a taken ship become a campaign asset, and if so does it enter the same
  facility model as the company ship? If yes, the home and prize distinction is a
  state rather than a kind, and the noun model should say so.
- Does inter-deck movement ever become a mission structure, given law 1 keeps it
  out of map topology?

## Out of scope

Everything about the mission itself. Do not let objectives, extraction, or reward
leak into the generator while this is parked.
