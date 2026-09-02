# Polity ground doctrine

Status: ACTIVE

Written: 2026-09-02
nUpdated: 2026-09-02 — implemented by `derived-polity-roster.md`.

Read `meta-progression.md` for the company-and-polity boundary this sits
inside, `campaign-battle-bridge-nouns.md` for the one path by which a faction's
troops reach a battle, and `progression-nouns.md` for the grade, pattern, and
experience vocabulary this reuses rather than restates.

## Purpose

Vanilla gives the player's faction a fleet it does not directly command:
patrols and defence fleets built from the blueprints the faction knows, at a
quality set by its best producing market, in numbers set by its markets' size
and stability, shaped by a handful of zero-sum doctrine choices. A new faction
fields generic hulls full of D-mods, and better industry fixes that.

The polity's ground forces should work the same way, and most of the pieces
exist. Every vanilla faction already has a **ground doctrine**: a
`GroundRosterProfile`, hand-authored per faction, holding what its bulk and
elite troops carry by risk. The mod's four-step equipment grade already has a
D-mod in it, Surplus. What is missing is a roster for the player faction that
is **derived** from the polity's markets rather than authored, a way for the
company's collected kit to reach it, and a side in the battle for those troops
to stand on. This document owns the model; the stories own the work.

## The parallel

| Vanilla fleet side | Polity ground side |
| --- | --- |
| Faction doctrine points: ship quality, numbers, officer quality | Doctrine choices: quality, numbers, heavy support. Zero-sum, few, consequential. |
| Known blueprints | **Released kit**: templates the company has released to the polity, on top of the Common-access floor any market sells. |
| Best producing market's ship quality: Heavy Industry or Orbital Works, less supply deficits, less the import penalty when nothing is produced | **Ground production quality**, read from the same vanilla stats on the polity's own markets, driving the grade table. |
| D-mods on spawned hulls | Surplus grade and the militia pattern. A polity with no industry fields worn Common kit. |
| Stability scaling fleet numbers | Stability scaling militia headcount. |
| Officer quality | Nothing separate. Experience is issued by armour (`progression-nouns.md`), so the pattern tier the polity can issue is its militia's experience band. |

The last row is the one that keeps the model honest. A training axis would be
a second experience authority beside issued armour, which the progression law
forbids; the polity's troops get better the way the company's do, by wearing
better suits.

## Vocabulary

- **Polity ground doctrine** is the player faction's ground doctrine: a
  `GroundRosterProfile` like every other faction's, except that it is derived.
- A **derived roster** is rebuilt from the polity's live markets when the
  economy updates, the way vanilla's ship-quality manager rebuilds fleet
  quality, and registered under the player faction id. It is never authored
  and never edited by hand.
- **Released kit** is a template card the company has released to the polity.
  A card is permanent collected capability and is never consumed
  (`progression-nouns.md`), so a release is a grant of the *definition*, not a
  transfer of stock. The Common-access floor is released by default; Advanced
  and Prestige kit only by an explicit act.
- **Ground production quality** is the polity's capacity to make what it
  knows: the best of its markets' production, read from vanilla's own
  industry, deficit, and stability stats. It selects the grade table. Knowing
  a Masterwork Tier IV pattern and being able to make it are different facts,
  which is the whole point of the blueprint parallel.
- An **allied garrison** is the protected market's own troops, fighting beside
  the company under their own faction and their own command. On the polity
  they wear the derived roster; on a patron's market they wear that faction's
  authored one.

## The model

The derived roster has four inputs and one output.

1. **Kit.** Released templates plus the Common floor give the candidate
   primaries, patterns, and specials. Nothing outside that set appears,
   however good the polity's industry is.
2. **Grade.** Ground production quality selects the grade weights per force
   tier: no production is Surplus-heavy with the import penalty; Heavy
   Industry admits Service and Milspec; Orbital Works with no deficits admits
   a Masterwork tail. Deficits in supplies and heavy armaments pull the table
   down the way metals deficits pull vanilla's ship quality down.
3. **Numbers.** Headcount at a market is its vanilla ground-defence stat with
   the company's own stationed strength subtracted, so a detachment never
   counts twice, scaled by stability the way vanilla scales fleet numbers.
4. **Doctrine.** Zero-sum choices, few enough to be a decision: quality moves
   the elite share and tightens the grade table; numbers raises headcount;
   heavy support admits a mech lance at markets with the industry to fabricate
   one. These live somewhere diegetic on the colony, not on a slider screen,
   and there is no fourth axis until a consumer needs one.

The output is one `GroundRosterProfile` for the player faction id, resolved
through the bridge's single path with no second resolve path and no faction
parameter threaded through setup. A market's own signals reach the battle the
way they always have, through the target profile; the roster reaches it
through faction identity.

## Consumers

- **The polity defence** (`polity-defence-raid-hook.md`): the polity's allied
  garrison stands beside the company against the landing. This is the first
  producer of the allied side (`ai-nouns.md`, Sides); today it is sized by a
  placeholder read off the target profile, until the derived headcount above
  exists.
- **A Garrison defence on a patron's market**: the same producer fields the
  patron faction's militia beside the company, from its authored roster. The
  polity is only the case where the roster is derived.
- **The absent case**: the derived roster's quality may feed the market's
  ground-defence stat as a small multiplier, the way vanilla doctrine feeds
  patrol quality. Optional and second.

The company's own recruits and Armory are never consumers. The polity does not
issue to the company, and the company's marines never consult a roster;
otherwise the boundary in `meta-progression.md` dissolves.

## Under the growth rule

The doctrine consumes Capability, since released kit is the company's
collection lent outward, and consumes Reach, since the industry that makes the
kit good is a colony investment. It produces a safer venue rather than a
company currency. It is first reachable at Stage 2, with a colony. Its
counter-pressure is vanilla's own colony economics, and later the MRB scrutiny
`meta-progression.md` reserves for a company that arms its own state.

## Laws

1. The polity's roster is derived, never authored, and is rebuilt from the
   live economy. A hand edit to it is a bug.
2. Released kit grants a definition, never stock, and never flows back. The
   company's Armory is not changed by a release.
3. Grade comes from production quality and nothing else. Doctrine may tighten
   the table; it may not admit a grade the industry cannot make.
4. Experience comes from issued armour, as everywhere. No training axis.
5. Allied troops fight under their own faction, their own command, and their
   own casualty accounting. They are never player units flagged not to take
   orders.
6. The roster reaches the battle through the bridge's one resolve path.

## Open questions

- Whether a release should cost anything beyond scrutiny. Leaning: no, the
  industry gate is the cost, exactly as in vanilla.
- Whether the polity's doctrine choices should be its own surface or ride on
  vanilla's faction doctrine screen, which the mod cannot extend. Leaning: a
  small panel in the colony's Marine Ops dialog.
- Whether ground production quality should read vanilla's
  `PRODUCTION_QUALITY_MOD` directly, inheriting every ship-side modifier, or
  reassemble the ground-relevant subset. Direct is simpler and drifts with
  vanilla; the subset is truer and needs upkeep.
