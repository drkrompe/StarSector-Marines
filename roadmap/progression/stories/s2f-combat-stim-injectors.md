# S2F — Combat stim injectors

Status: PLANNED — uses the shared special-equipment item seam and requires one
bounded temporary infantry-handling effect.

Written: 2026-08-24

Read `progression-nouns.md`, `company-view-nouns.md`, and `ai-nouns.md` before
implementing this story.

## Problem

Combat-stim and injector flavor currently appears only in faction prose. If it
is implemented as an invisible Pather or pirate modifier, the player cannot
identify the carrier, understand the timing, recover the equipment, or compare
it against grade and experience. If it is implemented as permanent extra
accuracy, it duplicates the existing weapon-quality and marine-profile axes.

Neural and HUD uplinks create the same temptation: “advanced targeting” can
become an unexplained faction-wide hit bonus or hidden access to enemy state.

## Goal

Add combat stims as one finite, readable special-equipment utility with a
temporary handling effect and an explicit recovery cost. Keep neural/HUD
uplinks in their honest integration boundary rather than minting an attachment
slot or an omniscient targeting bonus.

## Stim identity

A stim injector supplies a short emergency window in which a living marine can
maintain alertness and weapon handling under movement, injury, or heavy fire.
It does not heal structure, restore armor, prevent death, erase morale, or
increase weapon damage and penetration.

Initial direction:

- one or two finite doses per carrier;
- a readable injection channel followed by a bounded active duration;
- reduced—but not removed—moving-fire spread/handling penalty and new-threat
  reaction delay;
- a short aftershock that gives back some of that tempo without damaging or
  killing the carrier off-screen;
- caps that prevent a Green marine on stims from simply becoming an Elite
  marine and preserve the value of steady firing posture.

Exact duration, handling bounds, and aftershock are balance values owned by the
item's typed effect profile. Faction patterns may vary cost, presentation, or a
bounded trade, but all use the same activation and combat rules.

## Use policy and counterplay

- The carrier considers a dose only during an honest active-contact episode
  when its current mission, survival, or maneuver action can use the temporary
  handling window. It does not inject at battle start or because hidden enemies
  exist nearby.
- A dose is consumed when injection completes. Death or interruption before
  completion spends nothing; the active effect survives ordinary replanning
  but ends on death or its own timer.
- The effect modifies only named infantry-handling inputs. It cannot override
  target legality, line of sight, friendly-fire holds, squad orders, movement
  limits, or durability.
- Player and defender carriers use the same policy. Path and pirate rosters may
  weight illicit variants more heavily, but fanaticism is not immunity to the
  aftershock or incoming damage.
- Presentation shows the injection and active/aftershock state without making
  particles or tint the effect authority.

## Neural and HUD uplink boundary

Integrated targeting optics connect compatible weapons and powered suits in
the setting, especially in Tri-Tachyon and elite Hegemony issue. In the first
equipment pass they are provenance, catalog copy, and visible interface detail,
not another passive Armory slot.

Any later uplink mechanic must name a distinct decision that grade, aptitude,
experience, squad beliefs, and command do not already own. It may consume only
legal observed/remembered contacts and may never calculate squad fire arcs from
hidden live enemies. Faster target registration, shared contact briefing, or
ballistic assistance each require their own explicit authority contract; this
story ships none of them by implication.

## Campaign and presentation

- The injector occupies the existing single special slot, uses finite Armory
  stock, and has a stable item id, recipe, icon, carrier layer, use pose, HUD
  dose count, and active-state explanation.
- Templates express issue only. They do not encode “inject on assault” or a
  faction-specific battle script.
- Legitimate medical/mercenary patterns and illicit Path/pirate patterns may
  have different provenance and recovery pools while sharing behavior.
- `s6-unlock-ladder-expansion.md` owns player acquisition. A defender-only
  illicit variant must be explicitly marked as such rather than failing the
  stranded-player-asset check.

## Acceptance

- Injection, active duration, and aftershock are deterministic from the frozen
  item definition and battle clock.
- The effect changes only the named handling/reaction inputs and cannot alter
  damage, penetration, armor, structure, line of sight, target knowledge, or
  mission authority.
- Focused scenarios show a useful active window, a meaningful aftershock, no
  battle-start waste, and identical player/defender policy.
- Green-plus-stim and Elite-without-stim comparisons prove the item does not
  collapse persistent experience into consumable equipment.
- Template ownership, cargo-backed issue, deployment freeze, HUD state, save repair,
  telemetry-relevant actions, recovery, and unlock reachability recognize the
  injector.
- Neural/HUD language creates no stat or AI behavior without a separately
  contracted mechanic.

## Out of scope

- Healing, resurrection, armor repair, immunity to morale or suppression, and
  permanent addiction/injury systems.
- A new attachment grid or passive cybernetic inventory.
- Campaign legality, narcotics markets, or medical treatment consequences.
- Neural control of drones, squad-wide telepathy, or hidden-enemy targeting.
