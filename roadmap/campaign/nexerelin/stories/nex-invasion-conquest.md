# Nexerelin invasion as Conquest

Status: IN PROGRESS — slices 0–3 built and unit-tested; the owner's live pass is what remains.

Written: 2026-10-15

Updated: 2026-10-15 — Slices 1–3 built; live pass checklist added.

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

### Slice 0 findings (from source, 2026-10-15)

Answered by reading Nexerelin `9b0ddac` rather than by playing it: a live
install only helps once someone plays, and the owner runs one live pass at the
end. Questions 4 and 5 are partly live-only and are carried into that pass.

**Verdict: proceed with a named workaround.** The workaround is the order in
which the handoff battle is built.

1. **Settlement without turns works if `start()` runs.** `endBattle` calls
   `responseScript.forceDone()` unguarded, and the response script is created
   only by `start()` (`addMilitaryResponse`). So `init()` alone is not enough.
   `start()` also calls `generateDefenders()`, which sets
   `currNormalBaseStrength`, so the defender-victory division is safe.
   `start()` has one unwanted effect: when the battle is player-initiated it
   calls `autoGeneratePlayerUnits()`, which takes the cargo marines and heavy
   arms into Nexerelin units. The workaround: set player-initiated for
   `init()` (so the player is the attacking commander), clear it for `start()`,
   and set it again before `endBattle`. `endBattle` then reads it for the
   garrison-damage reset, the commission decision and the loot path.
   `handleTransfer` runs `InvasionRound.conquerMarket` to the attacker's
   faction, or to the commissioning faction, or back to the player for a
   former player market. A faction that sets `CUSTOM_NO_WAR_SIM` gets no
   response script, and `endBattle` would throw after the transfer. The
   handoff therefore catches and logs it. The settlement ledger is claimed
   before the handoff, so nothing retries it, and the company's own writeback
   does not depend on it.
2. **Defender victory.** With no turns played, the surviving defender strength
   equals the starting strength, so Nexerelin records no garrison damage. The
   alpha leaves that value: a repelled invasion leaves the garrison fresh.
   Feeding in the fraction the Conquest killed (through Nexerelin's own
   `GBUtils.setGarrisonDamageMemory`) needs the defender casualty count on the
   mission outcome, and is left for a later story. An attacker victory keeps
   Nexerelin's own reset (0.75 damage). Loot is zero, because no player unit
   was deployed; the battle's own salvage is ours.
3. **Side effects.** `start()` adds the intel to the manager and registers it
   as a script and a listener; `endAfterDelay` retires it. The player sees an
   ended ground-battle entry, which is a correct record. Listeners: with
   player-initiated cleared during `start()`, `reportPlayerJoinedBattle` does
   not fire and `PlayerInSystemTracker` does not mark the player as spotted;
   both are acceptable. `reportBattleEnded` fires once and the "space and land
   battle" achievement completes on a win, which is earned.
   `invadeInitV2`'s commitment effects are not part of `start()`, so the
   handoff mirrors them once, at settlement: the reputation hit (size × −0.01,
   at best hostile), `Misc.increaseMarketHostileTimeout(market, 60)`, the
   `$nex_recentlyInvaded` flag for 60 days, and vanilla's `$raid_cooldown`.
   The flag is the one direct Nexerelin memory write. It is recorded as an
   exception to law 4 in `nexerelin-nouns.md`, because without it Nexerelin
   would let the player invade again at once.
4. **Dialog return (live pass).** `handleTransfer` fires `PopulateOptions` on
   the current rule-based dialog, if there is one. The Marine Operations
   battle runs on its own full-canvas dialog, and the settlement happens on
   Results. The live pass confirms that the market menu is correct after both
   outcomes.
5. **Rule precedence (live pass).** Nexerelin's `nex_marketHostileInvadeSel`
   row has no score. Our row is `$option == nex_mktInvade score:100` plus a
   condition command that is false whenever Nexerelin is absent or the
   preference is Nexerelin. Without Nexerelin the option never exists, so the
   row is inert. Our "use Nexerelin" option does not name Nexerelin's command.
   It sets a one-shot bypass flag and re-fires the Invade selection, so
   Nexerelin's own row runs exactly as it would have. The fork's Back reuses
   Nexerelin's own `nex_mktInvadeGoBack`.
6. **Compiling against Nexerelin.** A compile-only stub module, `:nex-stubs`,
   declares only the signatures the handoff calls. It is `compileOnly` for the
   mod, and `verifyModJarBoundary` keeps its classes out of the jar. Every
   class that touches it is reached only after
   `Global.getSettings().getModManager().isModEnabled("nexerelin")`, and the
   call is wrapped against `LinkageError`. Pure reflection was rejected
   because it hides signature drift until play; depending on the installed
   jar was rejected because it breaks the build without Nexerelin.

## Slice 1 — preference and fork

- The preference is stored per save, in the mod's campaign state, so it
  survives save and reload and needs no LunaLib dependency. A new save starts
  at Ask. The fork's "always" answers set it, and the market menu offers "Ask
  again" whenever it is set. That was chosen over a control on a Marine
  Operations screen, because the market is where the question is asked.
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
- Backing out of the briefing returns to the market menu with nothing
  committed. An invasion may launch understrength: it is the player's own
  decision with whatever the company has.

## Slice 3 — handoff settlement

- On Results, map the outcome: a won Conquest is attacker victory; defeat and
  confirmed Retreat are defender victory. Settle once: replaying Results must
  not end a second handoff battle.
- Create and end the handoff battle as the spike established, inside the
  isolated compatibility package. Any linkage failure is logged, and the
  player is told the result could not be applied. No partial write is made.
- Personnel, boat and loot writeback follow their ordinary authorities. No
  contract settlement and no MRB credibility (law 6).

## Built (2026-10-15)

Slices 1–3 are built and unit-tested. None of it has been run in a game with
Nexerelin. In brief: `:nex-stubs` is the compile-only stub module.
`MarineInvasionCMD` and the `smNexInvade*` rows are the fork.
`NexInvasionMissionFactory` builds the Conquest, and
`MissionDefencePolicy.garrisonFaced` makes launch meet the whole garrison.
`NexInvasionSettlement` settles once by serial, mirrors the commitment and
ends the handoff battle through `NexGroundBattleHandoff`, the only class that
names a Nexerelin type.

### Live pass checklist

Install Nexerelin 0.12.2d with LazyLib and MagicLib, then:

1. Without Nexerelin: the game loads, and no fork or reset option appears.
2. Invade a planet's market under Ask: the fork shows its four choices and
   Back. Back returns to the defences menu.
3. "Leave it to Nexerelin": Nexerelin's own invade menu opens and behaves as
   stock (question 5).
4. "Fight it with Marine Operations": the briefing opens against the whole
   garrison. Back out: the market menu returns and nothing changed hands.
5. Win the Conquest: the market transfers (to the commissioning faction if
   commissioned), an ended ground-battle intel entry appears, reputation and
   the invasion cooldown apply once, and the market menu is correct on return
   (question 4).
6. Lose or retreat: the market stays with its owner, company losses are
   recorded, and Nexerelin records the failed invasion.
7. "Always" answers stick across save and reload, and "Ask again" on the
   market menu clears them.
8. Save, reload and re-enter the market after each outcome: no repeated
   settlement and no orphaned intel.
9. A station market goes straight to Nexerelin's own Invade.

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
