# Armor drop selection

Status: PLANNED — vehicles in the bay shipped (`mechs-nouns.md`, gantries); lift
limits from `air-nouns.md` (Lift; `LiftAdmission` is the check to spend against).
Written: 2026-10-16

Direction: `heavy-equipment-progression.md`. Models: `mechs-nouns.md` (Mech
Support, lances), `command-powers-nouns.md` (powers, charges, cooldowns).

## Outcome

Calling armor is a choice, not a lance. The company ship carries four bay
spaces, each a mech or a vehicle. When the player calls Mech Support they pick
which machines come down on this call; a cooldown gates the next call, and the
company's lift caps how much armor can reach the ground in one mission.

## Scope

- In-battle picker over the frozen bay manifest: each entry shows chassis or
  vehicle, weight class, callsign and whether lift allows it. Picking a subset
  dispatches only those; the rest stay aboard for a later call.
- Mission lift budget: the sum of available carriers' lift (by class) bounds the
  armor delivered per mission. A Heavy machine spends Heavy lift; an empty budget
  disables the call with the reason shown.
- Cooldown between calls replaces "one charge per lance".
- The AI commander's mech drop allowance is unchanged.

## Constraints

- What lands is still frozen at launch; the picker chooses from the manifest, it
  never edits a fit.
- A machine already landed or lost cannot be called again this mission.

## Acceptance

- Unit: manifest freezing, subset dispatch, lift-budget refusal, cooldown.
- A scene calling a Hound, then a vehicle after the cooldown, with the Bulwark
  refused for lift, frames at 1920x1080 / 100%.
