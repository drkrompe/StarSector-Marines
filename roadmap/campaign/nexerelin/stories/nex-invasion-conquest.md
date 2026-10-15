# Nexerelin invasion as Conquest

Status: PLANNED — slice 0 (spike) is next.

Written: 2026-10-15

Updated: 2026-10-15 — Per-save preference and fixed map scale decided; defenders come from `market-garrison-model.md`.

Read `nexerelin-nouns.md` in full first. Also read `meta-progression.md` (the
company and the polity), `architecture.md` (external-state boundary),
`campaign-battle-bridge-nouns.md`, `mission-tier-nouns.md` and
`retreat-defeat-settlement.md`.

Researched against Nexerelin 0.12.2d (GitHub `Histidine91/Nexerelin` at
`9b0ddac`, 2026-09-26), built for 0.98a-RC8. Nexerelin is not installed in the
local game copy today, so slice 0 starts by installing it there.

## Goal

When Nexerelin is installed, a player who invades a hostile market can choose
to fight the invasion as a Marine Operations Conquest instead of Nexerelin's
abstract ground battle. Nexerelin applies the result exactly as if its own
battle had ended that way. Without Nexerelin, nothing changes.

## Scope

- The ground combat preference: Nexerelin, Marine Operations, or ask each time
  (the default).
- Intercepting Nexerelin's Invade selection (option id `nex_mktInvade`) and
  offering the fork.
- A Marine Operations invasion briefing against the target market, using the
  existing Conquest family, target profile and launch path.
- Settlement through a handoff battle that is ended through
  `GroundBattleIntel.endBattle`.
- Isolating the Nexerelin dependency so the build and the game both work
  without it.

Out of scope: joined battles, colony invasions, AI wars, per-industry results,
loot from the market, and reporting company marines to Nexerelin's strength
calculation.

## Constraints

- Every law in `nexerelin-nouns.md`. Laws 1–5 are the ones this story is most
  likely to break.
- Never write market ownership, reputation or Nexerelin memory directly. If
  the spike shows that `endBattle` cannot settle a battle that never ran a
  turn, stop and revise the design. Do not fall back to calling
  `InvasionRound.conquerMarket` ourselves.
- `StarsectorMarines.jar` must not contain Nexerelin classes.
  `verifyModJarBoundary` must keep passing.
- Player-facing text goes through the localization catalog
  (`alpha-localization.md`).

## Slice 0 — spike: can a handoff battle settle?

Answer these with Nexerelin installed in the local game, in a throwaway
branch, before writing production code:

1. **Settlement without turns.** Create the battle the way
   `Nex_MarketCMD.prepIntel` does: `new GroundBattleIntel(market, attacker,
   defender)`, then `setPlayerInitiated(true)`, `setPlayerIsAttacker(true)`
   and `init()`. Then call `endBattle(ATTACKER_VICTORY)`. Does the market
   transfer to the right faction, with or without a commission, and do
   reputation, stability and the recently-invaded cooldown apply? `endBattle`
   calls `responseScript.forceDone()` and, for a defender victory, divides by
   `defender.currNormalBaseStrength`. Find out which of `init()` and `start()`
   (and the units they create) must have run to make both safe.
2. **Defender victory.** The same for `DEFENDER_VICTORY`. Garrison damage memory
   is set from surviving defender strength. Decide whether to leave Nexerelin's
   computed value or to feed in the fraction of defenders the Conquest
   actually killed.
3. **Side effects of a battle that never ran.** Check what intel entries,
   notifications, `GroundBattleCampaignListener` callbacks and achievements
   fire. If a callback reports a battle the player never saw, find out whether
   `start()` can be skipped.
4. **Dialog return.** `handleTransfer` refreshes the current rule-based dialog
   with `PopulateOptions`. Confirm that returning from the Marine Operations
   full-canvas dialog lands on a correct market menu after both outcomes.
5. **Rule precedence.** Confirm that a row in our `rules.csv` with
   `$option == nex_mktInvade score:10` wins over Nexerelin's
   `nex_marketHostileInvadeSel`. Confirm that our "use Nexerelin" option can
   fire `Nex_MarketCMD invadeMenu` with Nexerelin's behaviour unchanged.
   Confirm that without Nexerelin the row is inert, because the option never
   exists.
6. **Compiling against Nexerelin.** Choose how to compile without requiring
   Nexerelin to be installed. Recommended: a compile-only stub module with the
   few Nexerelin signatures we call, kept out of the jar by
   `verifyModJarBoundary`. Alternatives are pure reflection, or a compile-only
   dependency on the installed jar when present (which breaks the build on a
   machine without Nexerelin). Record the choice and why in this story.

Exit: a short findings section here, and one of three verdicts: proceed;
proceed with a named workaround; or redesign settlement.

## Slice 1 — preference and fork

- The preference is stored per save, in the mod's campaign state, so it
  survives save and reload and needs no LunaLib dependency. A new save starts
  at Ask. The Marine Operations screen must be able to change it.
- The intercepting rule and its command, gated on `nexerelin` being enabled and
  on the Invade option being supported (the alpha has only one supported entry
  point).
- The Ask fork text states the trade honestly: a played battle that settles
  now, with no time for fleets to intervene; or Nexerelin's battle over several
  days.
- Unit tests for the pure decision: preference, entry point and Nexerelin
  presence choose a resolver; an unsupported entry point always chooses
  Nexerelin.

## Slice 2 — invasion briefing

- A Marine Operations invasion operation targeting the market, with mission
  family Conquest, the ordinary target profile, company squads and committed
  lift.
- **Map scale is independent of market size** (decided). Every invasion is
  fought on Conquest's authored map, whatever the size of the market. Market
  size is a population dial and is purely environmental. It reaches the
  battle through the target profile's settlement scale (how built-up the
  ground is and how many people live on it), as it does for every other
  operation. It never sets the map's dimensions.
- The defenders are the market garrison estimate (`market-garrison-model.md`,
  law 7): headcount and mix from size, stability, structures and supply, with
  Nexerelin's recent-damage memory read when it is present. That story is a
  dependency of this slice. A player invasion faces the whole estimate, not a
  tier-capped share. Conquest's tier floor governs which generated offers are
  valid; a player invasion is not an offer, so a weak garrison is not refused.
  It is simply a light defence on the full map. If testing shows that a weak
  garrison on the full map needs special handling, raise it with the user
  rather than switching profile.
- Calibration against Nexerelin's own estimate is part of
  `market-garrison-model.md` slice 3, run with Nexerelin installed.
- Backing out of the briefing returns to the market's defences with nothing
  committed.

## Slice 3 — handoff settlement

- On Results, map the outcome: a won Conquest is attacker victory; defeat and
  confirmed Retreat are defender victory. Settle once: replaying Results must
  not end a second handoff battle.
- Create and end the handoff battle as the spike established, inside the
  isolated compatibility package. Any linkage failure is logged, and the
  player is told the result could not be applied. No partial write is made.
- Personnel, boat and loot writeback follow their ordinary authorities. No
  contract settlement and no MRB credibility (law 6).

## Acceptance

- Without Nexerelin: the game loads, no new options appear, and the built jar
  holds no Nexerelin classes.
- With Nexerelin and the preference set to Nexerelin: invading behaves exactly
  as stock Nexerelin.
- With Nexerelin and Ask: the fork appears only where Nexerelin enabled
  Invade. Each branch leads to its resolver.
- A won Conquest transfers the market, either to the player faction or to the
  commissioning faction, and applies Nexerelin's reputation, stability and
  cooldown consequences once.
- A lost or retreated Conquest leaves the market with its owner. Company
  losses are recorded, and Nexerelin records a defended invasion.
- Save, reload and re-enter the market after each outcome: there is no
  orphaned intel, no repeated settlement, and the market menu is correct.
- Live acceptance runs with Nexerelin and its dependencies (LazyLib,
  MagicLib) installed, and is recorded in the alpha tracker.
