# Campaign → Battle Bridge

Status: ACTIVE — target-profile transport is campaign-free, while ground consumers own interpretation.

Written: 2026-08-23

Updated: 2026-08-24 — replaced work sequencing with durable consumer, target-market, and authored-Conquest force boundaries.

Read `stories.md` for open work.

## Purpose

The campaign → battle bridge makes a battle's target world legible on the
ground without making battle generation depend on the live campaign API. It
turns the vanilla market selected by a mission into a small, immutable
description of that world, then carries that description into map generation.
The bridge is a shared boundary: it does not itself decide terrain, defenders,
or objectives; individual consumers make those decisions from the signals they
need.

## Vocabulary and ownership

- A **target market** is the vanilla-economy market associated with the
  mission's target planet. It is campaign data, not the mod's `CampaignState`.
- A **target profile** is the campaign-free snapshot of relevant target-market
  signals: settlement scale and stability, defensive hardening, port capacity,
  faction identity, and the presence of economic roles. It contains only plain
  values and bridge-owned vocabulary; it never retains a game API object.
- **Economic functions** are the stable, presence-only roles by which a market
  can be expressed on the ground: habitation, commerce, industry, port,
  extraction, refining, agriculture, and military activity. They are not
  vanilla industry ids and do not yet express size or throughput.
- **Neutral** is the absence of a target-market signal. It is used for
  headless generation, previews, unmatched targets, and intentionally isolated
  scenarios. It is a baseline world, not an arbitrary fallback world.
- A **consumer** is a battle or generation policy that elects to read a profile
  signal. Consumers own their own balance and presentation decisions.

`TargetProfileResolver` owns the one-way conversion from the live vanilla
market to the profile. `MissionLaunch` owns taking that snapshot at the mission
boundary. `BattleSetup` and `MapGenerator` own forwarding it without
interpreting campaign API. `BspCityGenerator` publishes it to the generation
recipe, while stages and zoning policies own their local use of it.

## Flow and boundaries

At launch, the selected mission identifies a target planet. The resolver finds
the matching vanilla market and produces one profile, or Neutral when no such
market is available. The mission builder passes that same value into its
battle setup. Setup passes it into map generation, and the BSP recipe exposes
it to its stages as generation context.

This is a directional, snapshot boundary. Battle and generation code do not
look up or hold `MarketAPI`; they may only consume the profile passed to them.
The launch path currently carries the profile through opening operations,
sabotage, civilian rescue, conquest, and the generic assault/raid/extraction
builder. Silent Colony deliberately generates from Neutral because its frozen
scenario facts, rather than the current market, own that map.

Non-BSP generators may ignore the profile. That is a valid no-op consumer,
not permission to reach back into the campaign.

## Standing behavior

### Neutral preservation

Neutral is the compatibility contract. A consumer receiving no market signal
must preserve its pre-bridge behavior and seeded determinism; in particular it
must not take an extra random draw merely to discover that the profile is
silent. The generator normalizes a missing profile to Neutral before stages
read it.

### Defensive hardening

The current defense consumer is the conquest overwatch line. A more defended
market creates a larger tower budget and promotes its tower weapon tier. Site
supply and ordinary placement constraints still cap what can be proposed.
Ordinary battle-start force balance admits only the static weapons the combined
attack and resolved defender roster can support; fortification geometry remains
when a weapon is trimmed. Conquest is deliberately different: its profile-aware
candidate line is part of an authored late-game siege, so battle setup retains
the line regardless of the attacking manifest. The market signal still does
not define the whole defender roster.

Its numerical balance is an in-game acceptance concern. Automated coverage may
prove that the profile changes the line and that Neutral remains baseline, but
it cannot certify that the resulting encounter feels fair or readable.

### Economic character

Economic functions make terrain selection reflect what the target world does.
The shared zoning policy gives distinctive roles priority over the nearly
universal habitation signal, so a farming, commercial, military, or industrial
world does not collapse into generic housing. On conquest maps this currently
biases the city band; on the legacy district path it biases interior selection.
On ordinary urban maps, a port-bearing market also claims an authored
spaceport campus whose scale follows port tier. Conquest maps instead keep the
port as a structural geographic band; neither path turns every inhabited
market into tarmac.

Economic districts own the terrain vocabulary and tactical identity built on
this substrate. A district is more than decoration: its filler must make its
cover, walkability, sightlines, and connectivity true on the navigation map.
The bridge carries only the economic signal; the economic-districts feature
owns the district-content program.

## Laws for future consumers

1. Keep the procedural core campaign-free. Add a plain profile signal and map
   it at the launch boundary; do not import vanilla campaign types into battle
   generation.
2. Extract once, then pass the same snapshot inward. A new consumer should not
   repeat the market lookup or create a second source of target-world truth.
3. Neutral is behavior-preserving. New signal-aware selection must leave the
   silent path deterministic and equivalent to its prior behavior.
4. Profile signals are descriptive, not automatic authority. Each consumer
   states what it changes and preserves existing tactical/map invariants.
5. Keep economic content separate from economic extraction. The bridge owns the
   vocabulary and transport; map generation owns how an economy becomes
   playable terrain.
6. Treat numeric encounter tuning as manual acceptance. Record representative
   in-game observations; do not substitute a new automated test for that
   judgment.

## Extension boundary

Hard installations may become real terrain consumers, but their owning map
feature defines geometry, tactical meaning, and validity. A unified defender
heavy-armament decision requires `TargetProfile` to become the sole
target-market read; `target-profile-defense-authority-cleanup.md` owns removal
of the current duplicate market-derived seam.

Per-function weights and new economic district types remain economic-district
content rather than bridge policy. `BattleSetup` now consumes the profile's
plain faction id once to resolve a battle-frozen ground roster; the roster
registry owns that interpretation and never reaches back into the campaign API.
Faction-specific cosmetics, morale from stability, and campaign outcomes still
require consumers that name their authority and preserve these boundary laws.
