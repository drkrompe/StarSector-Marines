# Campaign → Battle Bridge

Status: ACTIVE — target-profile transport is campaign-free, while ground consumers own interpretation.

Written: 2026-08-23

Updated: 2026-09-02 — the player faction's derived roster resolves through the same single path as every authored one.

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
  faction identity, the presence of economic roles, and the market's own ground
  strength. It contains only plain values and bridge-owned vocabulary; it never
  retains a game API object.
- **Ground defence** and **stationed strength** are the profile's two strength
  numbers. The first is vanilla's own defender strength for the market — its
  ground-defence stat, with vanilla's stability scaling and industry
  multipliers already inside it, so no consumer may scale by stability again.
  The second is the company's own contribution to that same number, read back
  off the modifiers the mod itself applied there. A consumer that turns strength
  into troops subtracts the second from the first, so a stationed detachment is
  never fielded twice; see `polity-ground-doctrine.md`.
- **Economic functions** are the stable, presence-only roles by which a market
  can be expressed on the ground: habitation, commerce, industry, port,
  extraction, refining, agriculture, and military activity. They are not
  vanilla industry ids and do not yet express size or throughput.
- **Neutral** is the absence of a target-market signal. It is used for
  headless generation, previews, unmatched targets, and intentionally isolated
  scenarios. It is a baseline world, not an arbitrary fallback world.
- A **consumer** is a battle or generation policy that elects to read a profile
  signal. Consumers own their own balance and presentation decisions.
- A **defender-faction override** is a mission-authored replacement for the
  profile's faction identity, applied once at the launch boundary. It says who
  holds the target world for this launch; it says nothing about what the target
  world is.

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

### Faction identity is an axis of its own

Faction identity is the one profile signal no generation stage reads. It has
exactly one consumer — the resolution of the battle-frozen ground roster — while
terrain follows scale, hardening, port capacity, and economic functions. That
separation is not incidental; it is the property that makes the defending
faction a *controlled* variable. Two launches on the same seed with different
faction identities are the same battlefield with different troops standing on
it, so anything that differs between them is attributable to doctrine rather
than to a reroll of the map.

A mission may therefore name a defender-faction override, and the launch
boundary rewrites only the profile's faction identity before handing the
snapshot inward. There is one such application point, and there is deliberately
no second resolve path and no faction parameter threaded through battle setup:
the guarantee above is only worth as much as the number of places that can
break it. A mission with no override is byte-identical to one that never had
the concept.

The player faction's own roster keeps to the same path. `polity-ground-doctrine.md`
derives it from the polity's live markets rather than authoring it, and
registers the result under the player faction id, so a battle on a
player-owned world resolves its troops exactly as a battle on anyone else's:
one faction id, one lookup. The market's other signals still reach generation
only through the target profile.

Comparing defenders is only a comparison when the battlefield holds still, so a
mission built for that purpose also pins its generation seed; ordinary missions
keep rerolling their map on each launch. The debug board carries such a group
for Conquest, derived from the roster catalog rather than from a faction list
written in code, so a newly catalogued doctrine becomes comparable without a
code change.

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
7. Leave faction identity out of generation. A stage that wants to vary terrain
   by owner is asking for a new descriptive signal — architectural style, say —
   not for the faction id, which would silently cost the bridge its one
   controlled axis.

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
