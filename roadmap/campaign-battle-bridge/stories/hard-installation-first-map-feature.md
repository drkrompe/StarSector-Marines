# First hard-installation map feature — local shield relay

Status: PLANNED — first vertical selected; paired with command-power shield-zone consumption.
Written: 2026-08-23

Updated: 2026-08-24 — selected a Conquest-local planetary-shield relay and bounded its campaign signal, map placement, and neutralization authority.

Read `campaign-battle-bridge-nouns.md` before changing this story.

## Problem

The profile's composite defense level can scale generic overwatch, but it
cannot distinguish a planetary shield from stations, batteries, command, or
ordinary ground defenses. The result is a harder generic firing line rather
than a visible reason the fleet above cannot bombard the decisive ground
objective.

The first hard installation is a **local shield relay** on Conquest maps backed
by a market with a planetary shield. It is one node in the planetary envelope,
not a claim that a fire team can switch off the world-wide shield. Neutralizing
it opens the local tactical footprint to fire support while the rest of the
planet remains protected.

## First vertical

1. Add one explicit, vanilla-decoupled planetary-shield presence signal to the
   target profile. Do not infer presence later from the composite defense level.
2. On Conquest only, place one relay inside a valid defender compound whose
   approach and footprint survive the recipe's connectivity and deployment
   gates. The canonical keep is preferred when the seeded placement is valid;
   placement failure omits the optional relay rather than corrupting the map.
3. Publish the relay structure, host compound, and authored coverage footprint
   as battle-facing map facts. Coverage is geometry, not a global mission flag.
4. The relay remains operational while its structure lives and its host
   compound is DEFENDER_HELD or CONTESTED. Capturing the compound intact makes
   it inoperable for the defender without creating a second capture model;
   destroying the structure disables it irreversibly.
5. `shielded-fire-support-zones.md` consumes only that operational coverage
   fact for Orbital Barrage targeting. Map generation does not inspect command
   decks or implement power activation.

The relay must read as a hardened power-and-field installation through
geometry, cover, and approach lanes before faction-specific treatment is added.
`target-faction-facility-treatment.md` owns the later visual/spatial treatment
layer and may not change relay function.

## Acceptance

- A planetary-shield market can produce exactly one local relay on a valid
  Conquest map; a market without that industry and Neutral cannot receive one.
- No vanilla campaign type crosses into battle or generation code.
- Seeded placement is deterministic, survives ordinary generation validation,
  and cannot partition required paths or invalidate deployment sites.
- The feature's defended approach, hardened footprint, host compound, and
  coverage boundary are readable without relying only on a label.
- DEFENDER_HELD and CONTESTED keep coverage operational; MARINE_HELD and
  structural destruction remove it. Recapture reactivates only an intact relay.
- Neutral and unrelated target profiles retain their prior seeded behavior.
- Headless coverage proves the relay fact reaches battle setup without a
  campaign object and that maps without a valid placement remain playable.

## Out of scope

- A whole-planet shield simulation or multiple-relay network.
- Orbital batteries, ground-defense nodes, and station-derived installations;
  `orbital-battery-fire-support-lock.md` owns the next fire-support denial form.
- Replacing force balance, overwatch intensity, or defender roster authority.
- Economic district content or objective-result writeback;
  `faction-ground-contract-policy.md` owns the mission/outcome seam.
- New capture progression. The host compound remains the sole reversible
  territory authority.
- Loot or schematic awards. `intact-installation-recovery.md` consumes the
  eventual frozen intact/destroyed outcome.
