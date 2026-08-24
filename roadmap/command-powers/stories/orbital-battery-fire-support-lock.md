# Orbital-battery fire-support lock

Status: DEFERRED — follows the local-shield installation vertical and owns the second bounded hard-installation placement.

Written: 2026-08-24

Read `command-powers-nouns.md`, `campaign-battle-bridge-nouns.md`, and
`mapgen-nouns.md` before accepting this story.

## Problem

Heavy batteries are currently folded into a numeric defense level. They make a
map harder but do not create the fiction or mechanic that forces the player's
fleet to remain outside the surface-to-orbit firing envelope. Orbital Barrage
therefore remains immediately available even though the target is explicitly
protected by heavy anti-orbital weapons.

## Goal

Represent a heavy-battery market with one ground-scale fire-control emplacement
whose operational state globally withholds Orbital Barrage. The fleet still
controls orbit, but cannot approach or hold a firing solution until marines
capture the emplacement's host compound or destroy its fire-control structure.

The first slice denies only Orbital Barrage. It does not pretend to model
damage, CR loss, or crew casualties on the supporting fleet, and it does not
reuse a barrage cooldown as fake counter-battery risk.

## Scope

1. Add an explicit heavy-battery presence/tier signal at the existing target
   profile resolution boundary.
2. Place one bounded fire-control emplacement through the Conquest recipe,
   reusing the hard-installation placement and compound-ownership seam proven
   by `hard-installation-first-map-feature.md`.
3. While the intact emplacement is defender-held or contested, Orbital Barrage
   remains in the deck but cannot be targeted anywhere. The denial reason is
   **fleet held at standoff by active surface-to-orbit fire control**.
4. Marine capture opens fire support without destroying valuable
   infrastructure; defender recapture restores the lock if the emplacement is
   intact. Destruction opens fire support irreversibly.
5. Briefing states controlled orbit, standoff restriction, the ground objective
   that lifts it, and any contract mandate that requires the site intact.

## Faction expression

The battery has one shared function. `target-faction-facility-treatment.md`
may give Hegemony, League, Diktat, Tri-Tachyon, Church/Path, pirate, and
Independent sites different geometry and presentation, but may not change the
lock, ownership, or barrage-payment laws. Faction tactical behavior around the
site belongs to `target-faction-command-doctrine.md`.

## Acceptance

- Heavy-battery markets can produce the emplacement; ordinary ground defenses,
  unrelated defense sources, and Neutral cannot.
- An operational emplacement blocks all new barrage commitments without
  removing the power from the frozen battle roster or spending resources.
- Capture, contest, recapture, and destruction obey the same state rules as the
  local shield relay and cannot disagree between UI and simulation.
- A mission without Orbital Barrage remains playable and explains the battery
  as the reason supporting ships are at standoff rather than presenting an
  irrelevant broken control.
- Live acceptance confirms that taking the emplacement creates a legible
  before/after change in the assault without making bombardment mandatory for
  victory.

## Out of scope

- Ship damage, crew loss, CR attrition, or forced retreat.
- Light AA, shuttle interception, fighter risk, or LZ coverage; those remain
  `s6-drop-geography.md`.
- Ground-defense networks without heavy batteries.
- Multiple battery sites or a planet-wide counter-battery campaign.
- Campaign industry disruption/writeback and intact recovery settlement.
