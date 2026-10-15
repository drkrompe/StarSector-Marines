# Nexerelin compatibility nouns

Status: DRAFT — co-existence laws are the direction; the first supported entry
point is planned in `nex-invasion-conquest.md`.

Written: 2026-10-15

## Purpose

Nexerelin is a popular optional mod that adds faction war to the campaign and
its own abstract ground battle. Many of our players will run both. This feature
owns how the two mods co-exist. It also owns the one boundary where a player
may choose which mod fights a ground battle.

It does not own a second invasion system. When the player chooses Marine
Operations, the battle is an ordinary operation through the existing launch,
battle and Results pipeline. Nexerelin then applies the result through its own
code, exactly as if its own battle had ended that way.

Read `meta-progression.md` (the company and the polity, and the vanilla raid
seam), `architecture.md` (the external-state boundary) and
`campaign-battle-bridge-nouns.md` (target profile) alongside this document.

## Vocabulary

- **Nexerelin** ("Nex", mod id `nexerelin`) is an optional soft dependency.
  The mod must load and play identically when it is absent.
- A **Nex ground battle** is Nexerelin's `GroundBattleIntel`: a battle over one
  market that lasts several in-game days. Opposing units occupy the market's
  industries, a turn resolves every few days, and response fleets may intervene
  between turns. It ends in one `BattleOutcome`: attacker victory, defender
  victory, peace, destroyed, cancelled or other. Nexerelin's own end of battle
  applies everything that follows: the market changing hands, reputation,
  stability, garrison damage, governorship and revenge invasions.
- A **Nex entry point** is one of the ways Nexerelin starts a ground battle:

  | Entry point | Who starts it | Alpha resolver |
  | --- | --- | --- |
  | **Player invasion**: the Invade option on a hostile market | The player | Player's choice |
  | **Joined battle**: the player joins an existing battle on either side | Another faction | Nexerelin |
  | **Colony invasion**: an AI faction invades a player-owned market | AI faction | Nexerelin |
  | **AI war**: one AI faction invades another | AI faction | Nexerelin, untouched |

  Nexerelin's Raid option is not an entry point. It is vanilla's raid with
  Nexerelin's adjustments and starts no Nex ground battle.
- A **resolver** is the system that decides a battle: **Nexerelin** (its own
  abstract battle) or **Marine Operations** (a played battle).
- A **supported entry point** is one for which Marine Operations has a mapped
  mission family that can stand in for the Nex battle. In the alpha, only the
  player invasion is supported, and it maps to Conquest.
- The **ground combat preference** is the player's standing choice for
  supported entry points: Nexerelin, Marine Operations, or ask each time. It
  belongs to the save, and a new save starts at Ask. It is never consulted for
  an unsupported entry point.
- A **handoff battle** is the Nex ground battle record created for a
  Marine-resolved invasion. It exists only so that Nexerelin's own
  end-of-battle path can apply the outcome. It never runs a turn.
- **Nex offensives** are Nexerelin's offensive fleet events: invasions, Nex
  raids, saturation bombardments, colony expeditions and defence fleets. They
  all subclass vanilla's `RaidIntel` through `OffensiveFleetIntel`. They are
  not vanilla raids, even though they share the class.

## Laws

1. **Nexerelin stays optional.** No Nexerelin class is loaded unless
   `nexerelin` is enabled. Code that touches Nexerelin lives in one isolated
   compatibility package, reached only behind that check. A linkage failure
   against an unexpected Nexerelin version must disable our choice and leave
   Nexerelin's own behaviour in place. It must never break the Invade option.
2. **Nexerelin decides whether; we decide only how.** Nexerelin's own
   conditions for invading a market stay authoritative: a defending fleet or
   station still to beat, an uninvadable market, too few marines, a cooldown.
   We intercept only the player's selection of an Invade option that
   Nexerelin has already offered and enabled.
3. **Choosing Nexerelin changes nothing.** That path runs Nexerelin's own
   commands unmodified. The fork comes before Nexerelin creates a battle or
   commits any marines, so neither resolver inherits half of the other's
   setup.
4. **Nexerelin applies the consequences.** A Marine-resolved battle reaches
   vanilla and Nexerelin state only by ending a handoff battle through
   Nexerelin's own end-of-battle path. The mod never transfers a market,
   changes reputation, or sets Nexerelin memory flags directly. This is the
   second sanctioned external writer beside the T3 endgame, and it follows the
   same rule as the vanilla raid seam: the mod chooses the outcome, and the
   owning system writes it.
5. **The outcome is binary in the alpha.** A won Conquest is attacker victory.
   Anything else, including a confirmed Retreat, is defender victory. There is
   no partial capture, per-industry occupation or loot from a Marine-resolved
   invasion until a later story maps compounds to industries.
6. **A player invasion is not a contract.** No patron hires it, it earns no MRB
   credibility and settles no contract, the same as a polity defence. Its
   campaign consequences inside the mod are the battle's own personnel, boat
   and loot facts, settled by their ordinary authorities. Who gains the market
   is Nexerelin's decision (the player faction or the commissioning faction).
7. **The resolver must not change how hard a market is.** The defenders come
   from the mod's own market garrison estimate (`market-garrison-model.md`).
   That is the same estimate every market-backed operation uses, computed the
   same way with or without Nexerelin, and it follows the same inputs
   Nexerelin's garrison does: size, stability, structures, supply and recent
   damage. Choosing Marine Operations must not be a way around a hard garrison
   or into an easy one.
8. **Unsupported entry points are left alone.** The mod never alters a joined
   battle, a colony invasion or an AI war, and never reads Nex ground battles
   as its own threats. Supporting another entry point is a new story that
   names its mission family and how it ends the Nex battle.
9. **Nex offensives are not vanilla raids.** The vanilla raid seam, which arms
   a Garrison defence and ends a raid through `abort` or `forceFail`, excludes
   the `OffensiveFleetIntel` lineage, identified by class name so that
   excluding it needs no Nexerelin dependency. Ending a Nex invasion through
   vanilla's raid path while its ground battle runs would corrupt Nexerelin's
   own state.

## Flow: a Marine-resolved player invasion

1. The player beats the market's defences. Nexerelin offers and enables
   Invade.
2. The player selects Invade. Our rule fires ahead of Nexerelin's, reads the
   ground combat preference, and either:
   - Nexerelin: runs Nexerelin's own invade menu, and the battle is entirely
     Nexerelin's from here;
   - Marine Operations: opens the Marine Operations invasion briefing;
   - Ask: shows both choices, each leading to one of the paths above.
3. The briefing is an ordinary operation against the target market: target
   profile, operation tier, Conquest map, company squads and committed lift.
   Backing out returns to the market's defences dialog with nothing
   committed.
4. The battle plays and settles through the ordinary Results path.
5. Settlement creates the handoff battle with the player as the initiating
   attacker and ends it with the mapped outcome. Nexerelin applies everything
   that follows, and the dialog returns to the market's refreshed options.

## Mismatches the design accepts

- **Time.** A Nex ground battle takes days, and fleets can intervene. A Marine
  Operations battle settles in one sitting. Response fleets therefore have no
  window to intervene. That is the main thing the player trades away, so the
  choice must say so.
- **Marines in cargo.** Nexerelin measures invasion strength from cargo
  marines and heavy armaments, and founding a company squad consumes cargo
  marines. A player who builds a company and then chooses Nexerelin invades
  with whatever cargo is left. The company's squads are invisible to Nexerelin
  until a later story decides whether to report them.
- **Map scale.** Market size never chooses the map. Every Marine-resolved
  invasion is fought on Conquest's authored map. Market size is a population
  dial: it reaches the battle only as environment, through the target
  profile's settlement scale (how built-up the ground is and how many people
  live on it). It never sets the map's dimensions. Size does reach the
  garrison, but only through the garrison estimate (law 7), as it does in
  vanilla and Nexerelin. Conquest's tier floor governs generated offers, not a
  player invasion.

## Boundaries

- `meta-progression.md` owns the polity and the vanilla raid seam. Law 9
  narrows that seam's input; it does not change the seam.
- `t3-endgame-nouns.md` remains the only owner of political market transitions
  that the mod itself initiates. A Nex handoff ends a battle that the player
  started through Nexerelin, and Nexerelin performs the transition.
- `mission-tier-nouns.md` and `precincts.md` own tier and map. This feature
  only supplies the target and the requested family.
- Colony invasions are the natural next supported entry point. They would be
  answered as a polity defence through Nexerelin's own
  `GroundBattleCampaignListener` and ended through the same handoff path.
