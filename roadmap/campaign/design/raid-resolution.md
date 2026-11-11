# Raid resolution: leading the player's own raid

Status: ACTIVE — built and unit-tested; the live pass is `player-raid-fork.md`.

Written: 2026-11-07

## Purpose

When the player takes hostile action against a market and launches a raid, the
game resolves it from the fleet's marines: an effectiveness from attacker
against defender strength, then rolled losses and loot. This feature lets the
player lead that raid as a Marine Operations battle instead, or keep the
standard resolution. It works the same with and without Nexerelin.

It does not own a second raid system. The game's own raid command applies every
consequence; the battle only decides what that command commits.

Read `campaign-nouns.md` (authority boundaries) and `nexerelin-nouns.md`
(co-existence laws) alongside this document.

## Vocabulary

- The **raid command** is whichever command runs the market's Take Hostile
  Action menu: vanilla's `MarketCMD`, or Nexerelin's `Nex_MarketCMD`, which
  extends it and replaces vanilla's rows. Both keep the raid being set up on the
  market as the same **raid record** (`$MarketCMD_temp`): its type, its
  objectives and the effectiveness already computed from strength.
- A **player raid** is a raid for valuables or to disrupt an industry, on a
  planet's market, that the player has set up and is about to confirm.
- A **supported raid** is a player raid Marine Operations can fight. A station,
  a raid on something that is not a market, and a mission's custom-only raid
  are not; they always resolve the standard way.
- The **raid fork** is the choice offered at the confirm: **Lead the Raid
  (Marines Mod)**, or **Order Raid (vanilla)**, the **standard resolution**
  (owner's wording).
- A **led-only raid** is a supported raid the fleet's marines are too few for:
  its effectiveness is under the raid command's threshold for its kind (a
  twentieth for valuables, a quarter to disrupt). Only the company can carry it
  out.
- The **raid fork setting** is the Settings page's "Ask before raids" key,
  saved with the game. On (a new save's default), every supported raid asks;
  off, every raid resolves the standard way untouched. The fork itself has no
  "always" answer: the player chooses each raid (owner). It is separate from
  the Nexerelin invasion fork setting, so a player may fight invasions by hand
  and let raids roll.
- A **led raid** is a supported raid fought as a Marine Operations Raid
  against the market's garrison, with no contract and no employer.

## Laws

1. **The raid command decides whether; the company's battle replaces only the
   cargo marines** (owner pick `raid-led-entry` a: "WE remove the 'cargo
   marine' requirement by having the players company do the battle"). The raid
   exists only where the game allows it: no fleet ready to offer battle, the
   raid cooldown clear, the hostility confirmation accepted. Its one gate on the
   fleet's marines, effectiveness under a threshold for the kind of raid, is
   the one thing set aside: the raid menu is the command's own, and any kind of
   raid it refused for want of marines is offered again with a line saying the
   company can lead it. Picking one marks the raid led-only and hands the pick
   to the command, which sets up its objectives with the tokens its own minimum
   allows; the fork then offers only Lead the Raid, Order Raid shown and
   disabled. Nothing else of the command's gate changes, and the
   effectiveness the command computed is left as it is.
2. **One rule, both mods, slightly ahead.** The fork's row outranks Nexerelin's
   by one point and vanilla's by two, never by a margin that would bury another
   mod's row. It names no Nexerelin command. Every path back to the game
   re-fires the confirm with a one-shot bypass, so whichever command is running
   takes it exactly as it would have.
3. **Choosing the standard resolution changes nothing.**
4. **The raid command applies the consequences.** A led raid reaches campaign
   state only through the raid command's own confirm: hostile timeout, military
   response, cooldown, stability, reputation, loot, disruption, raid listeners
   and the post-raid rules. Other content that listens for a finished raid (for
   example Nexerelin's disrupt-a-competitor mission) therefore sees an ordinary
   raid. The mod adjusts only the raid record, and only for the outcome:
   - **won**: committed as the market's full raid with its security's premium
     (law 9);
   - **lost or withdrawn**: committed with no objectives. The attack happened
     and is on record, but it takes nothing, disrupts nothing and reports no
     objective achieved.
5. **The fleet's marines did not fight.** A led raid zeroes the command's
   marine-loss roll for that one confirm. The company's own casualties, boats
   and experience settle through Results like any operation's.
6. **A player raid is not a contract.** It earns no MRB credibility, pays no
   fee and carries no salvage entitlement: its loot is the raid command's.
7. **Choosing the battle must not change how hard the market is.** The
   defenders come from the market's garrison estimate, the share the operation's
   tier engages, as for every market-backed operation, with what the market's
   security posts at its works on top (`raid-mission.md`, Security).
8. **A raid commits once.** Backing out of the briefing commits nothing and
   returns to the fork. A fought battle is recorded once at Results and
   committed once when the panel closes; a replayed result commits nothing.
9. **Leading on the ground pays more, and a harder market pays most** (owner:
   "We just need to offer more potential rewards for leading on the ground to
   compensate"). The company needs no marines in the hold, yet it can take a
   fortified works the standard raid would barely scratch. A won led raid is committed as the market's full raid whatever the cargo marines
   would have earned: at full effectiveness, its marine tokens (which the raid
   command reckons loot and disruption by) filled to the full raid's ten times
   the market's security premium (open none, guarded a quarter, fortified a
   half; untuned), spread over the objectives the player chose in proportion
   to their choice, and a disrupting raid's times the share of the works
   wrecked, so every hall brought down adds days down (`LedRaidReward`,
   `RaidOutcomeCommit`). The standard raid itself is never changed (law 3).
10. **The fork speaks the raid command's language** (owner: "I do NOT want to
    tell the player about mechanics like effectiveness UNLESS the vanilla game
    already conveys this"). It shows what the command's own plan shows the
    player: the loot it projects in credits, or the days it says the target goes
    down, ordered and led (the led figure is the command's own reckoning at the
    won led raid's tokens, a disrupting raid's "up to" the whole works), and what
    defends the market at its security. A led-only raid says the fleet's
    marines are too few instead of an ordered figure. It names no tokens,
    effectiveness, premium or company maximums.

## Flow

1. The player opens the raid menu, which the command builds; a kind of raid
   the fleet's marines are too few for is offered again (law 1). The player
   sets up a raid (valuables, or an industry to disrupt) and selects Launch
   raid.
2. The fork's row fires ahead of the command's. With the fork setting off, or
   for an unsupported raid, it steps aside and the command confirms the raid.
   Otherwise it shows the fork.
3. The briefing is an ordinary Raid against the market, targeting the industry
   for a disrupting raid. It may launch understrength.
4. The battle plays and settles through Results, which records won or lost.
5. When the panel closes, the raid record is adjusted for the outcome and the
   confirm is re-fired through the bypass, with the loss roll zeroed. The
   command commits the raid and shows its own result and spoils.

## Mismatches the design accepts

- **A won raid is graded by the market and the works, not the win.** A won
  battle earns the full raid at its security's premium, never more for a clean
  win; a disrupting raid earns that times the share of the works wrecked; a
  lost one earns nothing.
- **The story-point secret raid** confirms inside the command, not through a
  rule, so it always resolves the standard way, led-only or not: a led-only
  raid's secret confirm commits at the effectiveness and the few tokens the
  fleet's marines allowed.
- **Cargo marines still decide how many objectives a led raid may name.** The
  command hands out tokens by the fleet's marines (at least one), and the
  player spreads only those; a won led raid fills them to its full size over
  that choice (law 9), so a led-only raid names one objective.

## Boundaries

- `nexerelin-nouns.md` owns Nexerelin's invasion entry point and its handoff
  battle. Nexerelin's Raid is not an entry point there; it is the raid command
  this feature already reaches.
- `meta-progression.md` owns the vanilla raid seam, which is raids on the
  player's own colonies. This feature is the player's raids on others.
- Tactical and saturation bombardment, and engaging the defences, are fleet
  actions and stay with the game.
